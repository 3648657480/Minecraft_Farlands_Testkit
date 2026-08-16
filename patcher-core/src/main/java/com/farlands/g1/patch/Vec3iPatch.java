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
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds {@code getRealX/Y/Z()D} accessors to {@code Vec3i}, routed through
 * the unified {@code FarProjection} convention (origin-aware unwrap on
 * generation threads, unsigned fallback elsewhere, identity for normal
 * coordinates).
 */
public final class Vec3iPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/core/Vec3i";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (node.methods.stream().anyMatch(m -> "getRealX".equals(m.name))) {
            return original;
        }

        node.methods.add(realAccessor("getRealX", "x"));
        node.methods.add(realAccessor("getRealY", "y"));
        node.methods.add(realAccessor("getRealZ", "z"));

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode realAccessor(String name, String field) {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, name, "()D", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, "I"));
        if (field.equals("y")) {
            il.add(new InsnNode(Opcodes.I2D));
        } else {
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/farlands/g1/util/FarProjection",
                "unwrap" + field.toUpperCase(), "(I)D", false));
        }
        il.add(new InsnNode(Opcodes.DRETURN));
        return mn;
    }
}
