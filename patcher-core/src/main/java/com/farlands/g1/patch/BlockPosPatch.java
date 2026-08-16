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
 * Widens {@code BlockPos} position-deriving arithmetic to the long domain
 * and adds a (JJJ) constructor, matching the {@link Vec3iPatch} foundation.
 *
 * <p>The int accessors keep wrap semantics, so every existing consumer is
 * unchanged; wide values survive {@code offset/multiply/subtract/relative/
 * cross} and are readable through {@code getLongX/Y/Z()}.</p>
 */
public final class BlockPosPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/core/BlockPos";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (find(node, "<init>", "(JJJ)V") != null) {
            return original;
        }
        node.methods.add(longConstructor());
        rewrite(node, "offset", "(III)Lnet/minecraft/core/BlockPos;",
            newInstance("offset", new int[]{0, 1, 2}));
        rewrite(node, "multiply", "(I)Lnet/minecraft/core/BlockPos;",
            newInstance("multiply", new int[]{0, 1, 2}));
        rewrite(node, "subtract", "(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos;",
            subtractBody());
        rewrite(node, "relative", "(Lnet/minecraft/core/Direction;I)Lnet/minecraft/core/BlockPos;",
            relativeBody());
        rewrite(node, "cross", "(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos;",
            crossBody());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode longConstructor() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(JJJ)V", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/core/Vec3i", "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static InsnList newInstance(String op, int[] axes) {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (int i = 0; i < 3; i++) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i",
                "getLong" + "XYZ".substring(axes[i], axes[i] + 1), "()J", false));
            if ("multiply".equals(op)) {
                il.add(new VarInsnNode(Opcodes.ILOAD, 1));
                il.add(new InsnNode(Opcodes.I2L));
                il.add(new InsnNode(Opcodes.LMUL));
            } else {
                il.add(new VarInsnNode(Opcodes.ILOAD, i + 1));
                il.add(new InsnNode(Opcodes.I2L));
                il.add(new InsnNode(Opcodes.LADD));
            }
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList subtractBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (int i = 0; i < 3; i++) {
            String axis = "XYZ".substring(i, i + 1);
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLong" + axis, "()J", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLong" + axis, "()J", false));
            il.add(new InsnNode(Opcodes.LSUB));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList relativeBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (int i = 0; i < 3; i++) {
            String axis = "XYZ".substring(i, i + 1);
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLong" + axis, "()J", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Direction",
                "getStep" + axis, "()I", false));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new VarInsnNode(Opcodes.ILOAD, 2));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LMUL));
            il.add(new InsnNode(Opcodes.LADD));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList crossBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongY", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongZ", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongZ", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongY", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongZ", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongX", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongZ", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongY", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongY", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongX", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    private static void rewrite(ClassNode node, String name, String desc, InsnList body) {
        MethodNode m = find(node, name, desc);
        if (m == null) {
            throw new IllegalStateException(TARGET + "#" + name + desc + " not found");
        }
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body);
    }
}
