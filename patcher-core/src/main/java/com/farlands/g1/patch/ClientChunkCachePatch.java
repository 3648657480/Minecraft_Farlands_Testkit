package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Epoch-normalizes chunk coordinates in {@code ClientChunkCache}.
 *
 * <p>At extreme coordinates the client stores chunks under coordinates that
 * wrap around the 22-bit section epoch. This patch adds the same
 * {@code norm(int)} normalization used by the working fork build (shift the
 * negative extreme band by 2^28) and applies it to every storage lookup,
 * plus a linear fallback scan in {@code getChunk}. The {@code Storage.inRange}
 * check becomes a circular distance over the 2^28 period.</p>
 */
public final class ClientChunkCachePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/client/multiplayer/ClientChunkCache";
    private static final int SHIFT = 268_435_456; // 2^28
    private static final long HALF = 134_000_000L;

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (find(node, "norm", "(I)I") != null) {
            return original; // already patched
        }
        node.methods.add(normMethod());

        rewrite(node, "isValidChunk",
            "(Lnet/minecraft/world/level/chunk/LevelChunk;II)Z", isValidChunk());
        rewrite(node, "drop", "(Lnet/minecraft/world/level/ChunkPos;)V", drop());
        rewrite(node, "getChunk",
            "(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
            getChunk());
        rewrite(node, "updateViewCenter", "(II)V", updateViewCenter());
        rewrite(node, "onLightUpdate",
            "(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)V", onLightUpdate());
        rewrite(node, "onSectionEmptinessChanged", "(IIIZ)V", onSectionEmptinessChanged());
        rewrite(node, "updateViewRadius", "(I)V", updateViewRadius());
        rewrite(node, "replaceBiomes", "(IILnet/minecraft/network/FriendlyByteBuf;)V", replaceBiomes());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
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

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    private static MethodInsnNode normCall() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "norm", "(I)I", false);
    }

    private static MethodNode normMethod() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "norm", "(I)I", null, null);
        LabelNode plain = new LabelNode();
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new LdcInsnNode(-100_000_000));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, plain));
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new LdcInsnNode(SHIFT));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(plain);
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    // static isValidChunk(LevelChunk chunk, int x, int z):
    //   chunk != null && norm(chunk.getPos().x()) == x && norm(chunk.getPos().z()) == z
    private static InsnList isValidChunk() {
        InsnList il = new InsnList();
        LabelNode retFalse = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new JumpInsnNode(Opcodes.IFNULL, retFalse));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/LevelChunk", "getPos",
            "()Lnet/minecraft/world/level/ChunkPos;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "x", "()I", false));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, retFalse));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/LevelChunk", "getPos",
            "()Lnet/minecraft/world/level/ChunkPos;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "z", "()I", false));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, retFalse));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(retFalse);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    // drop(ChunkPos pos):
    //   x = norm(pos.x()); z = norm(pos.z());
    //   if (storage.inRange(x, z)) { index = storage.getIndex(x, z); cur = storage.getChunk(index);
    //     if (isValidChunk(cur, x, z)) storage.drop(index, cur); }
    private static InsnList drop() {
        InsnList il = new InsnList();
        LabelNode end = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "x", "()I", false));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ISTORE, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "z", "()I", false));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ISTORE, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "inRange", "(II)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "getIndex", "(II)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "getChunk",
            "(I)Lnet/minecraft/world/level/chunk/LevelChunk;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "isValidChunk",
            "(Lnet/minecraft/world/level/chunk/LevelChunk;II)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "drop",
            "(ILnet/minecraft/world/level/chunk/LevelChunk;)V", false));
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    // getChunk(int x, int z, ChunkStatus status, boolean loadOrGenerate)
    private static InsnList getChunk() {
        InsnList il = new InsnList();
        LabelNode scan = new LabelNode();
        LabelNode notFound = new LabelNode();
        // nx = norm(x); nz = norm(z)
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ISTORE, 6));
        // if (storage.inRange(nx, nz)) { chunk = storage.getChunk(storage.getIndex(nx, nz)); if (isValidChunk(chunk, nx, nz)) return chunk; }
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "inRange", "(II)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, scan));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "getIndex", "(II)I", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "getChunk",
            "(I)Lnet/minecraft/world/level/chunk/LevelChunk;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 7));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "isValidChunk",
            "(Lnet/minecraft/world/level/chunk/LevelChunk;II)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, scan));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new InsnNode(Opcodes.ARETURN));
        // fallback linear scan
        il.add(scan);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 7));
        LabelNode loop = new LabelNode();
        LabelNode continueLabel = new LabelNode();
        LabelNode returnNullLabel = new LabelNode();
        il.add(loop);
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET + "$Storage", "chunks",
            "Ljava/util/concurrent/atomic/AtomicReferenceArray;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/concurrent/atomic/AtomicReferenceArray", "length",
            "()I", false));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, notFound));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET + "$Storage", "chunks",
            "Ljava/util/concurrent/atomic/AtomicReferenceArray;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/concurrent/atomic/AtomicReferenceArray", "get",
            "(I)Ljava/lang/Object;", false));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/level/chunk/LevelChunk"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 8));
        il.add(new VarInsnNode(Opcodes.ALOAD, 8));
        il.add(new JumpInsnNode(Opcodes.IFNULL, continueLabel));
        il.add(new VarInsnNode(Opcodes.ALOAD, 8));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/LevelChunk", "getPos",
            "()Lnet/minecraft/world/level/ChunkPos;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "x", "()I", false));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, continueLabel));
        il.add(new VarInsnNode(Opcodes.ALOAD, 8));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/LevelChunk", "getPos",
            "()Lnet/minecraft/world/level/ChunkPos;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "z", "()I", false));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, continueLabel));
        il.add(new VarInsnNode(Opcodes.ALOAD, 8));
        il.add(new InsnNode(Opcodes.ARETURN));
        il.add(continueLabel);
        il.add(new org.objectweb.asm.tree.IincInsnNode(7, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        il.add(notFound);
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new JumpInsnNode(Opcodes.IFEQ, returnNullLabel));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "emptyChunk",
            "Lnet/minecraft/world/level/chunk/LevelChunk;"));
        il.add(new InsnNode(Opcodes.ARETURN));
        il.add(returnNullLabel);
        il.add(new InsnNode(Opcodes.ACONST_NULL));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    // updateViewCenter(int x, int z): storage.viewCenterX = norm(x); storage.viewCenterZ = norm(z);
    private static InsnList updateViewCenter() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(normCall());
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET + "$Storage", "viewCenterX", "I"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(normCall());
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET + "$Storage", "viewCenterZ", "I"));
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    // onLightUpdate(LightLayer layer, SectionPos pos):
    //   Minecraft.getInstance().levelExtractor.setSectionDirty(norm(pos.x()), pos.y(), norm(pos.z()));
    private static InsnList onLightUpdate() {
        InsnList il = new InsnList();
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/client/Minecraft", "getInstance",
            "()Lnet/minecraft/client/Minecraft;", false));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/Minecraft", "levelExtractor",
            "Lnet/minecraft/client/renderer/extract/LevelExtractor;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/SectionPos", "x", "()I", false));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/SectionPos", "y", "()I", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/SectionPos", "z", "()I", false));
        il.add(normCall());
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/extract/LevelExtractor",
            "setSectionDirty", "(III)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    // onSectionEmptinessChanged(int sx, int sy, int sz, boolean empty):
    //   storage.onSectionEmptinessChanged(norm(sx), sy, norm(sz), empty);
    private static InsnList onSectionEmptinessChanged() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "onSectionEmptinessChanged",
            "(IIIZ)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    // updateViewRadius(int viewRange)
    private static InsnList updateViewRadius() {
        InsnList il = new InsnList();
        LabelNode end = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET + "$Storage", "chunkRadius", "I"));
        il.add(new VarInsnNode(Opcodes.ISTORE, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "calculateStorageRange", "(I)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, end));
        // newStorage = new Storage(newChunkRadius)
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET + "$Storage"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET + "$Storage", "<init>",
            "(Lnet/minecraft/client/multiplayer/ClientChunkCache;I)V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET + "$Storage", "viewCenterX", "I"));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET + "$Storage", "viewCenterX", "I"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET + "$Storage", "viewCenterZ", "I"));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET + "$Storage", "viewCenterZ", "I"));
        // loop
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        LabelNode loop = new LabelNode();
        LabelNode afterLoop = new LabelNode();
        il.add(loop);
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET + "$Storage", "chunks",
            "Ljava/util/concurrent/atomic/AtomicReferenceArray;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/concurrent/atomic/AtomicReferenceArray", "length",
            "()I", false));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, afterLoop));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET + "$Storage", "chunks",
            "Ljava/util/concurrent/atomic/AtomicReferenceArray;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/concurrent/atomic/AtomicReferenceArray", "get",
            "(I)Ljava/lang/Object;", false));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/level/chunk/LevelChunk"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 6));
        LabelNode skip = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new JumpInsnNode(Opcodes.IFNULL, skip));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/LevelChunk", "getPos",
            "()Lnet/minecraft/world/level/ChunkPos;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 7));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "x", "()I", false));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "z", "()I", false));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ISTORE, 9));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new VarInsnNode(Opcodes.ILOAD, 9));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "inRange", "(II)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new VarInsnNode(Opcodes.ILOAD, 9));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "getIndex", "(II)I", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "replace",
            "(ILnet/minecraft/world/level/chunk/LevelChunk;)V", false));
        il.add(skip);
        il.add(new org.objectweb.asm.tree.IincInsnNode(5, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        il.add(afterLoop);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    // replaceBiomes(int chunkX, int chunkZ, FriendlyByteBuf readBuffer)
    private static InsnList replaceBiomes() {
        InsnList il = new InsnList();
        LabelNode notInRange = new LabelNode();
        LabelNode end = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(normCall());
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "inRange", "(II)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, notInRange));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET + "$Storage", "getIndex", "(II)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "storage",
            "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET + "$Storage", "chunks",
            "Ljava/util/concurrent/atomic/AtomicReferenceArray;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/concurrent/atomic/AtomicReferenceArray", "get",
            "(I)Ljava/lang/Object;", false));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/level/chunk/LevelChunk"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 7));
        LabelNode notPresent = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "isValidChunk",
            "(Lnet/minecraft/world/level/chunk/LevelChunk;II)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, notPresent));
        // LOGGER.warn("Ignoring chunk since it's not present: {}, {}", chunkX, chunkZ);
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, TARGET, "LOGGER", "Lorg/slf4j/Logger;"));
        il.add(new LdcInsnNode("Ignoring chunk since it's not present: {}, {}"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "org/slf4j/Logger", "warn",
            "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", true));
        il.add(new JumpInsnNode(Opcodes.GOTO, end));
        il.add(notPresent);
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/LevelChunk", "replaceBiomes",
            "(Lnet/minecraft/network/FriendlyByteBuf;)V", false));
        il.add(new JumpInsnNode(Opcodes.GOTO, end));
        il.add(notInRange);
        // LOGGER.warn("Ignoring chunk since it's not in the view range: {}, {}", chunkX, chunkZ);
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, TARGET, "LOGGER", "Lorg/slf4j/Logger;"));
        il.add(new LdcInsnNode("Ignoring chunk since it's not in the view range: {}, {}"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "org/slf4j/Logger", "warn",
            "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", true));
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }
}
