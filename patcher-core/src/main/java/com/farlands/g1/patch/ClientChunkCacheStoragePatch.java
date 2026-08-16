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
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Circular view-range check for {@code ClientChunkCache.Storage}.
 *
 * <p>{@code inRange} uses a straight {@code |x - center| <= radius} test,
 * which always fails once chunk coordinates wrap around the 2^28 epoch used
 * by {@link ClientChunkCachePatch#norm}. This patch computes the distance
 * over a 2^28 period in the long domain instead.</p>
 */
public final class ClientChunkCacheStoragePatch implements ClassPatch {

    private static final String STORAGE = "net/minecraft/client/multiplayer/ClientChunkCache$Storage";
    private static final String OUTER = "net/minecraft/client/multiplayer/ClientChunkCache";
    private static final int SHIFT = 268_435_456; // 2^28
    private static final long HALF = 134_000_000L;

    @Override
    public boolean matches(String internalName) {
        return STORAGE.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode inRange = null;
        for (MethodNode m : node.methods) {
            if ("inRange".equals(m.name) && "(II)Z".equals(m.desc)) {
                inRange = m;
                break;
            }
        }
        if (inRange == null) {
            throw new IllegalStateException(STORAGE + "#inRange not found");
        }
        inRange.instructions.clear();
        inRange.localVariables = null;
        inRange.tryCatchBlocks.clear();
        inRange.instructions.add(body());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static MethodInsnNode normCall() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, OUTER, "norm", "(I)I", false);
    }

    private static InsnList body() {
        InsnList il = new InsnList();
        LabelNode end = new LabelNode();
        // locals: this=0, chunkX=1, chunkZ=2, cx=3, cz=5, vx=7, vz=9, dx=11, dz=13
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(normCall());
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LSTORE, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(normCall());
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LSTORE, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, STORAGE, "viewCenterX", "I"));
        il.add(normCall());
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LSTORE, 7));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, STORAGE, "viewCenterZ", "I"));
        il.add(normCall());
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LSTORE, 9));
        // dx
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new VarInsnNode(Opcodes.LLOAD, 7));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(J)J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 11));
        LabelNode skipX = new LabelNode();
        il.add(new VarInsnNode(Opcodes.LLOAD, 11));
        il.add(new LdcInsnNode(HALF));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLE, skipX));
        il.add(new VarInsnNode(Opcodes.LLOAD, 11));
        il.add(new LdcInsnNode((long) SHIFT));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(J)J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 11));
        il.add(skipX);
        // dz
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));
        il.add(new VarInsnNode(Opcodes.LLOAD, 9));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(J)J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 13));
        LabelNode skipZ = new LabelNode();
        il.add(new VarInsnNode(Opcodes.LLOAD, 13));
        il.add(new LdcInsnNode(HALF));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLE, skipZ));
        il.add(new VarInsnNode(Opcodes.LLOAD, 13));
        il.add(new LdcInsnNode((long) SHIFT));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(J)J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 13));
        il.add(skipZ);
        // dx <= chunkRadius && dz <= chunkRadius
        il.add(new VarInsnNode(Opcodes.LLOAD, 11));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, STORAGE, "chunkRadius", "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, end));
        il.add(new VarInsnNode(Opcodes.LLOAD, 13));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, STORAGE, "chunkRadius", "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, end));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(end);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }
}
