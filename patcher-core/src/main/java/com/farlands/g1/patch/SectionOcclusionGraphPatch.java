package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Disables the vanilla octree frustum culling in {@code SectionOcclusionGraph}.
 *
 * <p>The octree distance math breaks down past 2^30 (the far lands render
 * pipeline handles culling itself). This patch replaces the frustum visit
 * with an iteration over all render sections (via the {@code getAllSections}
 * accessor added by {@link ViewAreaPatch}) and stops {@code update()} from
 * scheduling the vanilla partial/full frustum updates, matching the working
 * fork build.</p>
 */
public final class SectionOcclusionGraphPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/client/renderer/SectionOcclusionGraph";
    private static final String CRS = "net/minecraft/client/renderer/state/level/CameraRenderState";
    private static final String CLRS = "net/minecraft/client/renderer/state/level/ChunkLoadingRenderState";
    private static final String RENDER_SECTION =
        "net/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        rewrite(node, "addSectionsInFrustum",
            "(Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;Ljava/util/List;)V",
            addSectionsInFrustum());
        rewrite(node, "update", "(L" + CRS + ";IL" + CLRS + ";)V", update());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static void rewrite(ClassNode node, String name, String desc, InsnList body) {
        MethodNode m = null;
        for (MethodNode candidate : node.methods) {
            if (name.equals(candidate.name) && desc.equals(candidate.desc)) {
                m = candidate;
                break;
            }
        }
        if (m == null) {
            throw new IllegalStateException(TARGET + "#" + name + desc + " not found");
        }
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body);
    }

    private static InsnList addSectionsInFrustum() {
        InsnList il = new InsnList();
        LabelNode end = new LabelNode();
        LabelNode loop = new LabelNode();
        LabelNode skipNull = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "viewArea",
            "Lnet/minecraft/client/renderer/ViewArea;"));
        il.add(new JumpInsnNode(Opcodes.IFNULL, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "viewArea",
            "Lnet/minecraft/client/renderer/ViewArea;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/ViewArea", "getAllSections",
            "()Ljava/lang/Iterable;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/lang/Iterable", "iterator",
            "()Ljava/util/Iterator;", true));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        il.add(loop);
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true));
        il.add(new JumpInsnNode(Opcodes.IFEQ, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next",
            "()Ljava/lang/Object;", true));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, RENDER_SECTION));
        il.add(new VarInsnNode(Opcodes.ASTORE, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new JumpInsnNode(Opcodes.IFNULL, skipNull));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "add",
            "(Ljava/lang/Object;)Z", true));
        il.add(new InsnNode(Opcodes.POP));
        il.add(skipNull);
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    private static InsnList update() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, CLRS, "addedLoadedChunks",
            "Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, CLRS, "removedLoadedChunks",
            "Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "updateLoadedChunks",
            "(Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;)V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, CLRS, "addedEmptySections",
            "Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, CLRS, "removedEmptySections",
            "Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "updateEmptySections",
            "(Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }
}
