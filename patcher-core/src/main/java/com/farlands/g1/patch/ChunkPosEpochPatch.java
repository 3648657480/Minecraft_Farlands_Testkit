package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * E line: makes {@code ChunkPos}'s int view epoch-relative.
 *
 * <p>Runs after {@link ChunkPosPatch} (B line, long fields). Every
 * {@code GETFIELD x/z:J} whose value is narrowed with {@code L2I} becomes
 * an epoch-relative conversion via {@code FarProjection.epochChunkX/Z}.
 * This covers the accessors {@code x()/z()}, {@code asLong()},
 * {@code hashCode()}, {@code equals()} and the block-boundary accessors, so
 * the entire chunk storage domain (ChunkMap, client cache, structure
 * managers, WorldGenRegion centers) keys on one consistent local domain.
 * The wide {@code (JJ)} constructor and {@code xLong()/zLong()} keep the
 * real domain for generation. A {@code farlands$epoch} marker field lets
 * the mod detect this jar state.</p>
 */
public final class ChunkPosEpochPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/ChunkPos";
    private static final String PROJECTION = "com/farlands/g1/util/FarProjection";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName) || internalName.startsWith(TARGET + "$");
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (node.fields.stream().noneMatch(f -> "x".equals(f.name) && "J".equals(f.desc))) {
            return original; // requires the B-line widening
        }
        if (TARGET.equals(node.name)
            && node.fields.stream().noneMatch(f -> "farlands$epoch".equals(f.name))) {
            node.fields.add(new org.objectweb.asm.tree.FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "farlands$epoch", "I", null, 1));
        }

        boolean changed = false;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (!(insn instanceof FieldInsnNode fi) || fi.getOpcode() != Opcodes.GETFIELD
                    || !"J".equals(fi.desc) || !TARGET.equals(fi.owner)) {
                    continue;
                }
                boolean xAxis = "x".equals(fi.name);
                if (!xAxis && !"z".equals(fi.name)) {
                    continue;
                }
                AbstractInsnNode next = insn.getNext();
                if (next != null && next.getOpcode() == Opcodes.L2I) {
                    MethodInsnNode conv = new MethodInsnNode(Opcodes.INVOKESTATIC, PROJECTION,
                        xAxis ? "epochChunkX" : "epochChunkZ", "(J)I", false);
                    m.instructions.insert(insn, conv);
                    m.instructions.remove(next);
                    changed = true;
                }
            }
        }
        if (!changed) {
            return original;
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    @Override
    public String describe(String internalName) {
        return "ChunkPosEpochPatch (int view epoch-relative)";
    }
}
