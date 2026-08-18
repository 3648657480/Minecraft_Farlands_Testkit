package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * E line: makes {@code ChunkPos} block-boundary accessors epoch-relative.
 *
 * <p>Runs after {@link ChunkPosPatch} (B line, long fields). Only the
 * block-domain accessors ({@code getMinBlockX/Z}, {@code getMaxBlockX/Z})
 * become relative to the epoch origin via
 * {@code FarProjection.epochMinBlockX/Z}; the chunk storage domain
 * ({@code x()/z()/asLong()}) stays real - int chunk coordinates hold up to
 * +/-2^35 blocks, far beyond the +/-2^31 block boundary this milestone
 * crosses. The noise pipeline then operates on small local ints whose real
 * value is {@code epoch + local}. A {@code farlands$epoch} marker field
 * lets the mod detect this jar state.</p>
 */
public final class ChunkPosEpochPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/ChunkPos";
    private static final String PROJECTION = "com/farlands/g1/util/FarProjection";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (node.fields.stream().noneMatch(f -> "x".equals(f.name) && "J".equals(f.desc))) {
            return original; // requires the B-line widening
        }
        if (node.fields.stream().noneMatch(f -> "farlands$epoch".equals(f.name))) {
            node.fields.add(new org.objectweb.asm.tree.FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "farlands$epoch", "I", null, 1));
        }

        boolean changed = false;
        for (String axis : new String[]{"X", "Z"}) {
            changed |= rewrite(node, "getMinBlock" + axis, false, axis);
            changed |= rewrite(node, "getMaxBlock" + axis, true, axis);
        }
        if (!changed) {
            return original;
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static boolean rewrite(ClassNode node, String name, boolean plusFifteen, String axis) {
        MethodNode m = null;
        for (MethodNode cand : node.methods) {
            if (name.equals(cand.name) && "()I".equals(cand.desc)) {
                m = cand;
                break;
            }
        }
        if (m == null) {
            throw new IllegalStateException(TARGET + "#" + name + " not found");
        }
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, axis.toLowerCase(), "J"));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROJECTION,
            "epochMinBlock" + axis, "(J)I", false));
        if (plusFifteen) {
            il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 15));
            il.add(new InsnNode(Opcodes.IADD));
        }
        il.add(new InsnNode(Opcodes.IRETURN));
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(il);
        return true;
    }

    @Override
    public String describe(String internalName) {
        return "ChunkPosEpochPatch (block accessors epoch-relative)";
    }
}
