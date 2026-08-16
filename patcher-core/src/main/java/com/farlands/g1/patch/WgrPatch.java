package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Prevents the "Requested chunk unavailable during world generation" crash
 * at extreme coordinates.
 *
 * <p>When the region center is beyond 134M chunks the requested coordinates
 * wrap around the 22-bit section epoch and the cache lookup legitimately
 * fails. Vanilla throws a crash report; the working fork build instead
 * returns the center chunk so generation keeps running. This patch inserts
 * the same guard in front of the throw site.</p>
 */
public final class WgrPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/WorldGenRegion";
    private static final String GET_CHUNK_DESC =
        "(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;";
    private static final int EXTREME = 134_000_000;

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode getChunk = null;
        for (MethodNode m : node.methods) {
            if ("getChunk".equals(m.name) && GET_CHUNK_DESC.equals(m.desc)) {
                getChunk = m;
                break;
            }
        }
        if (getChunk == null) {
            throw new IllegalStateException(TARGET + "#getChunk" + GET_CHUNK_DESC + " not found");
        }

        for (AbstractInsnNode n : getChunk.instructions) {
            if (n instanceof LdcInsnNode ldc && ldc.cst instanceof Integer v && v == EXTREME) {
                return original; // already patched
            }
        }

        AbstractInsnNode anchor = null;
        for (AbstractInsnNode n : getChunk.instructions) {
            if (n instanceof TypeInsnNode t && t.getOpcode() == Opcodes.NEW
                && "java/lang/IllegalStateException".equals(t.desc)) {
                anchor = n;
                break;
            }
        }
        if (anchor == null) {
            throw new IllegalStateException(TARGET + ": IllegalStateException throw site not found");
        }

        LabelNode retCenter = new LabelNode();
        LabelNode doThrow = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "centerChunkX", "I"));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(I)I", false));
        guard.add(new LdcInsnNode(EXTREME));
        guard.add(new JumpInsnNode(Opcodes.IF_ICMPGT, retCenter));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "centerChunkZ", "I"));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(I)I", false));
        guard.add(new LdcInsnNode(EXTREME));
        guard.add(new JumpInsnNode(Opcodes.IF_ICMPLE, doThrow));
        guard.add(retCenter);
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "center",
            "Lnet/minecraft/world/level/chunk/ChunkAccess;"));
        guard.add(new InsnNode(Opcodes.ARETURN));
        guard.add(doThrow);
        getChunk.instructions.insertBefore(anchor, guard);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
