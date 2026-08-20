package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Carries the wide chunk origin into {@code NoiseChunk}.
 *
 * <p>{@code forChunk} truncates the chunk position to int before
 * constructing, so the noise pipeline loses the true position beyond 2^31
 * (terrain repeats and tears along the wrap seam). This patch adds wide
 * origin fields and a constructor variant, and rewires {@code forChunk} to
 * pass {@code ChunkPos.xLong() &lt;&lt; 4}. The FarLands mod's mixin then
 * reconstructs continuous real coordinates in {@code getBlockXDouble()}:
 * {@code origin + (wrappedCellBlock - (int)origin)}, which is bit-identical
 * to vanilla for normal coordinates.</p>
 */
public final class NoiseChunkPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/levelgen/NoiseChunk";
    private static final String VANILLA_CTOR_DESC =
        "(ILnet/minecraft/world/level/levelgen/RandomState;IILnet/minecraft/world/level/levelgen/NoiseSettings;"
            + "Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;"
            + "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;"
            + "Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;"
            + "Lnet/minecraft/world/level/levelgen/blending/Blender;)V";
    private static final String WIDE_CTOR_DESC =
        "(ILnet/minecraft/world/level/levelgen/RandomState;IIJJLnet/minecraft/world/level/levelgen/NoiseSettings;"
            + "Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;"
            + "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;"
            + "Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;"
            + "Lnet/minecraft/world/level/levelgen/blending/Blender;)V";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (node.fields.stream().anyMatch(f -> "farlands$originX".equals(f.name))) {
            return original;
        }

        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$originX", "J", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$originZ", "J", null, null));
        node.methods.add(wideConstructor());
        rewriteForChunk(node);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /** (…, int minBlockX, int minBlockZ, long originX, long originZ, …rest) delegating to the vanilla ctor. */
    private static MethodNode wideConstructor() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", WIDE_CTOR_DESC, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new VarInsnNode(Opcodes.ALOAD, 10));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new VarInsnNode(Opcodes.ALOAD, 12));
        il.add(new VarInsnNode(Opcodes.ALOAD, 13));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", VANILLA_CTOR_DESC, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$originX", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 7));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$originZ", "J"));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    /**
     * forChunk: additionally compute xLong()&lt;&lt;4 / zLong()&lt;&lt;4 and invoke the wide ctor.
     */
    private static void rewriteForChunk(ClassNode node) {
        MethodNode forChunk = null;
        for (MethodNode m : node.methods) {
            if ("forChunk".equals(m.name)) {
                forChunk = m;
                break;
            }
        }
        if (forChunk == null) {
            throw new IllegalStateException(TARGET + "#forChunk not found");
        }
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            "net/minecraft/world/level/levelgen/NoiseGeneratorSettings", "noiseSettings",
            "()Lnet/minecraft/world/level/levelgen/NoiseSettings;", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/NoiseSettings",
            "clampToHeightAccessor",
            "(Lnet/minecraft/world/level/LevelHeightAccessor;)Lnet/minecraft/world/level/levelgen/NoiseSettings;",
            false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/ChunkAccess", "getPos",
            "()Lnet/minecraft/world/level/ChunkPos;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 7));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/NoiseSettings",
            "getCellWidth", "()I", false));
        il.add(new InsnNode(Opcodes.IDIV));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        // wide origins: xLong() << 4, zLong() << 4
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "xLong", "()J", false));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new VarInsnNode(Opcodes.LSTORE, 9));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "zLong", "()J", false));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new VarInsnNode(Opcodes.LSTORE, 11));
        // publish the generation origin for origin-aware unwrap
        il.add(new VarInsnNode(Opcodes.LLOAD, 9));
        il.add(new VarInsnNode(Opcodes.LLOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/farlands/g1/util/FarProjection",
            "setGenerationOrigin", "(JJ)V", false));
        // freeze the coordinate domain at chunk construction
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/farlands/g1/util/FarProjection",
            "isEpochActive", "()Z", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/farlands/g1/util/FarProjection",
            "setGenerationEpochCells", "(Z)V", false));
        // construct
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "getMinBlockX",
            "()I", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "getMinBlockZ",
            "()I", false));
        il.add(new VarInsnNode(Opcodes.LLOAD, 9));
        il.add(new VarInsnNode(Opcodes.LLOAD, 11));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", WIDE_CTOR_DESC, false));
        il.add(new InsnNode(Opcodes.ARETURN));
        forChunk.instructions.clear();
        forChunk.localVariables = null;
        forChunk.tryCatchBlocks.clear();
        forChunk.instructions.add(il);
    }
}
