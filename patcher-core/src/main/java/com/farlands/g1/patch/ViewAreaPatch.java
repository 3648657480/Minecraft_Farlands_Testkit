package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds {@code getAllSections()} to {@code ViewArea}, exposing the backing
 * section storage so the FarLands render mixins can iterate all render
 * sections for epoch-aware resorting.
 */
public final class ViewAreaPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/client/renderer/ViewArea";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        for (MethodNode m : node.methods) {
            if ("getAllSections".equals(m.name)) {
                return original; // already patched
            }
        }

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "getAllSections", "()Ljava/lang/Iterable;",
            "()Ljava/lang/Iterable<Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;>;",
            null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "sections",
            "Lnet/minecraft/client/RotatingSectionStorage;"));
        mn.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(mn);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
