package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * E line: translates epoch-relative chunk requests to real chunk
 * coordinates at the {@code WorldGenRegion.getChunk} boundary, using the
 * center-based "closer after translation" rule. The noise pipeline
 * (surface, biome, references) queries in the epoch-relative domain while
 * the region caches chunks under their real coordinates; real-domain
 * callers (structure managers) pass through untouched.
 */
public final class WorldGenRegionEpochPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/WorldGenRegion";
    private static final String PROJECTION = "com/farlands/g1/util/FarProjection";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode m = null;
        for (MethodNode cand : node.methods) {
            if ("getChunk".equals(cand.name)
                && "(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;"
                    .equals(cand.desc)) {
                m = cand;
                break;
            }
        }
        if (m == null) {
            throw new IllegalStateException(TARGET + "#getChunk(IILChunkStatus;Z) not found");
        }

        InsnList il = new InsnList();
        // Inline form (no extra locals): the vanilla getChunk uses local
        // slots 5+ for its own values, and a stored center clobbered them
        // (world-entry generation crash at the spawn even with the epoch
        // dormant). Read the center straight onto the stack instead.
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "centerChunkX", "I"));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROJECTION, "epochTranslatedChunkX", "(II)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "centerChunkZ", "I"));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROJECTION, "epochTranslatedChunkZ", "(II)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 2));
        m.instructions.insert(il);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    @Override
    public String describe(String internalName) {
        return "WorldGenRegionEpochPatch (center-based chunk translation)";
    }
}
