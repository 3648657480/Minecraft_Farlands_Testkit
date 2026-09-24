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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Prevents the "Requested chunk unavailable during world generation" crash.
 *
 * <p>Vanilla {@code WorldGenRegion.getChunk} hard-fails when the requested chunk
 * is not in the region cache <em>and</em> {@code |centerChunk| <= 134M}. The
 * epoch engine runs in the LOCAL domain, so the region center is small and this
 * throw path is always reachable - structure/feature decoration (e.g.
 * {@code MineshaftPieces.isInInvalidLocation} querying a biome outside the
 * region) then crashes chunk generation.</p>
 *
 * <p>The fix rewrites the throw path: while the epoch is active, an unavailable
 * chunk returns the region's center chunk (same degradation the fork build uses
 * beyond 134M) instead of throwing. With the epoch dormant, vanilla behavior is
 * unchanged.</p>
 */
public final class WgrPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/WorldGenRegion";
    private static final String PROJECTION = "com/farlands/g1/util/FarProjection";
    private static final String GET_CHUNK_DESC =
        "(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;";

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

        // Idempotency marker: our own injected call. (Do NOT key this on the
        // 134000000 constant - vanilla's outer guard already contains it, which
        // made the old check think the class was always already patched.)
        for (AbstractInsnNode n : getChunk.instructions) {
            if (n instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKESTATIC
                && PROJECTION.equals(mi.owner) && "isEpochActive".equals(mi.name)) {
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

        // Epoch active -> return this.center instead of throwing.
        LabelNode doThrow = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROJECTION, "isEpochActive", "()Z", false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, doThrow));
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
