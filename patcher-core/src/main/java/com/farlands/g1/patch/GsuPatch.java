package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Floating-origin patch for {@code GlobalSettingsUniform}.
 *
 * <p>The renderer loses float precision beyond ~2^30. This patch:</p>
 * <ul>
 *   <li>adds a {@code public static volatile Vec3 renderOrigin} field, and</li>
 *   <li>replaces {@code update(...)} with a delegation to
 *       {@code com.farlands.g1.runtime.GsuHelper.update(...)} (shipped in the
 *       FarLands mod), which snaps the UBO origin to a multiple of 16 blocks
 *       and uploads camera coordinates relative to it.</li>
 * </ul>
 *
 * <p>Only the patch mechanics live here; the numeric logic is original code
 * written for the FarLands project.</p>
 */
public final class GsuPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/client/renderer/GlobalSettingsUniform";
    private static final String HELPER = "com/farlands/g1/runtime/GsuHelper";
    private static final String UPDATE_DESC =
        "(IIDJLnet/minecraft/client/DeltaTracker;ILnet/minecraft/world/phys/Vec3;Z)V";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (node.fields.stream().anyMatch(f -> "renderOrigin".equals(f.name))) {
            return original;
        }

        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE,
            "renderOrigin", "Lnet/minecraft/world/phys/Vec3;", null, null));

        MethodNode clinit = findMethod(node, "<clinit>", "()V");
        if (clinit != null) {
            InsnList init = new InsnList();
            init.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/phys/Vec3", "ZERO",
                "Lnet/minecraft/world/phys/Vec3;"));
            init.add(new FieldInsnNode(Opcodes.PUTSTATIC, TARGET, "renderOrigin",
                "Lnet/minecraft/world/phys/Vec3;"));
            clinit.instructions.insertBefore(lastInsn(clinit), init);
        }

        MethodNode accessor = new MethodNode(Opcodes.ACC_PUBLIC, "farlands$buffer",
            "()Lcom/mojang/blaze3d/buffers/GpuBuffer;", null, null);
        accessor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        accessor.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "buffer",
            "Lcom/mojang/blaze3d/buffers/GpuBuffer;"));
        accessor.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(accessor);

        MethodNode update = findMethod(node, "update", UPDATE_DESC);
        if (update == null) {
            throw new IllegalStateException(TARGET + "#update" + UPDATE_DESC + " not found");
        }
        update.instructions.clear();
        update.localVariables = null;
        update.tryCatchBlocks.clear();
        update.instructions.add(delegateCall());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static InsnList delegateCall() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.DLOAD, 3));
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new VarInsnNode(Opcodes.ILOAD, 10));
        il.add(new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "update",
            "(Lnet/minecraft/client/renderer/GlobalSettingsUniform;IIDJLnet/minecraft/client/DeltaTracker;"
                + "ILnet/minecraft/world/phys/Vec3;Z)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    private static AbstractInsnNode lastInsn(MethodNode m) {
        AbstractInsnNode insn = m.instructions.getLast();
        while (insn != null && insn.getOpcode() < 0) {
            insn = insn.getPrevious();
        }
        return insn;
    }
}
