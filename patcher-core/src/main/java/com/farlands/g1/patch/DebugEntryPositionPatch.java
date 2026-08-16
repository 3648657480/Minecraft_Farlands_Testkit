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
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * F3 overlay: shows true long-domain coordinates.
 *
 * <p>The vanilla debug screen prints block/chunk coordinates as ints, which
 * wrap negative past -2^31. This patch rewrites {@code display(...)} to
 * format the block and chunk lines from {@code floor()} longs instead,
 * matching the working fork build.</p>
 */
public final class DebugEntryPositionPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/client/gui/components/debug/DebugEntryPosition";
    private static final String DISPLAY_DESC =
        "(Lnet/minecraft/client/gui/components/debug/DebugScreenDisplayer;Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/chunk/LevelChunk;)V";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode display = null;
        for (MethodNode m : node.methods) {
            if ("display".equals(m.name) && DISPLAY_DESC.equals(m.desc)) {
                display = m;
                break;
            }
        }
        if (display == null) {
            throw new IllegalStateException(TARGET + "#display not found");
        }
        display.instructions.clear();
        display.localVariables = null;
        display.tryCatchBlocks.clear();
        display.instructions.add(body());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static InsnList body() {
        InsnList il = new InsnList();
        LabelNode end = new LabelNode();
        LabelNode n1 = new LabelNode();
        LabelNode n2 = new LabelNode();
        LabelNode n3 = new LabelNode();
        LabelNode n4 = new LabelNode();
        LabelNode faceEnd = new LabelNode();
        LabelNode chunksElse = new LabelNode();
        LabelNode chunksEnd = new LabelNode();

        // minecraft = Minecraft.getInstance(); entity = minecraft.getCameraEntity(); if null return
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/client/Minecraft", "getInstance",
            "()Lnet/minecraft/client/Minecraft;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/Minecraft", "getCameraEntity",
            "()Lnet/minecraft/world/entity/Entity;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new JumpInsnNode(Opcodes.IFNULL, end));

        // feetPos = entity.blockPosition()
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "blockPosition",
            "()Lnet/minecraft/core/BlockPos;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 7));
        // direction = entity.getDirection()
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "getDirection",
            "()Lnet/minecraft/core/Direction;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 9));

        // faceString switch
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/core/Direction", "NORTH",
            "Lnet/minecraft/core/Direction;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new JumpInsnNode(Opcodes.IF_ACMPNE, n1));
        il.add(new LdcInsnNode("Towards negative Z"));
        il.add(new JumpInsnNode(Opcodes.GOTO, faceEnd));
        il.add(n1);
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/core/Direction", "SOUTH",
            "Lnet/minecraft/core/Direction;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new JumpInsnNode(Opcodes.IF_ACMPNE, n2));
        il.add(new LdcInsnNode("Towards positive Z"));
        il.add(new JumpInsnNode(Opcodes.GOTO, faceEnd));
        il.add(n2);
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/core/Direction", "WEST",
            "Lnet/minecraft/core/Direction;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new JumpInsnNode(Opcodes.IF_ACMPNE, n3));
        il.add(new LdcInsnNode("Towards negative X"));
        il.add(new JumpInsnNode(Opcodes.GOTO, faceEnd));
        il.add(n3);
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/core/Direction", "EAST",
            "Lnet/minecraft/core/Direction;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new JumpInsnNode(Opcodes.IF_ACMPNE, n4));
        il.add(new LdcInsnNode("Towards positive X"));
        il.add(new JumpInsnNode(Opcodes.GOTO, faceEnd));
        il.add(n4);
        il.add(new LdcInsnNode("Invalid"));
        il.add(faceEnd);
        il.add(new VarInsnNode(Opcodes.ASTORE, 10));

        // chunks = serverOrClientLevel instanceof ServerLevel ? getForceLoadedChunks() : LongSets.EMPTY_SET
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new TypeInsnNode(Opcodes.INSTANCEOF, "net/minecraft/server/level/ServerLevel"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, chunksElse));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/server/level/ServerLevel"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/level/ServerLevel",
            "getForceLoadedChunks", "()Lit/unimi/dsi/fastutil/longs/LongSet;", false));
        il.add(new JumpInsnNode(Opcodes.GOTO, chunksEnd));
        il.add(chunksElse);
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "it/unimi/dsi/fastutil/longs/LongSets", "EMPTY_SET",
            "Lit/unimi/dsi/fastutil/longs/LongSets$EmptySet;"));
        il.add(chunksEnd);
        il.add(new VarInsnNode(Opcodes.ASTORE, 11));

        // displayer.addToGroup(GROUP, list) where list = base lines + F3Helper extras
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ASTORE, 12));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, TARGET, "GROUP",
            "Lnet/minecraft/resources/Identifier;"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 13));

        // build List.of(5 strings)
        il.add(new InsnNode(Opcodes.ICONST_5));
        il.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));

        // 0: XYZ
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(xyzFormat());
        il.add(new InsnNode(Opcodes.AASTORE));
        // 1: Block
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(blockFormat());
        il.add(new InsnNode(Opcodes.AASTORE));
        // 2: Chunk
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(chunkFormat());
        il.add(new InsnNode(Opcodes.AASTORE));
        // 3: Facing
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(facingFormat());
        il.add(new InsnNode(Opcodes.AASTORE));
        // 4: dimension + FC
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(dimensionFormat());
        il.add(new InsnNode(Opcodes.AASTORE));

        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/List", "of",
            "([Ljava/lang/Object;)Ljava/util/List;", true));
        il.add(new VarInsnNode(Opcodes.ASTORE, 14));
        // finalList = new ArrayList(); finalList.addAll(base); finalList.addAll(F3Helper.extraLines(...));
        il.add(new TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 15));
        il.add(new VarInsnNode(Opcodes.ALOAD, 15));
        il.add(new VarInsnNode(Opcodes.ALOAD, 14));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "addAll",
            "(Ljava/util/Collection;)Z", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 15));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/farlands/g1/runtime/F3Helper", "extraLines",
            "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/level/Level;"
                + "Lnet/minecraft/world/entity/Entity;)Ljava/util/List;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "addAll",
            "(Ljava/util/Collection;)Z", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 12));
        il.add(new VarInsnNode(Opcodes.ALOAD, 13));
        il.add(new VarInsnNode(Opcodes.ALOAD, 15));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            "net/minecraft/client/gui/components/debug/DebugScreenDisplayer", "addToGroup",
            "(Lnet/minecraft/resources/Identifier;Ljava/util/Collection;)V", true));

        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    private static InsnList xyzFormat() {
        InsnList il = new InsnList();
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/util/Locale", "ROOT", "Ljava/util/Locale;"));
        il.add(new LdcInsnNode("XYZ: %.3f / %.5f / %.3f"));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(cameraCoord(0));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(cameraCoord(1));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(cameraCoord(2));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "format",
            "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false));
        return il;
    }

    private static InsnList cameraCoord(int axis) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity",
            axis == 0 ? "getX" : axis == 1 ? "getY" : "getZ", "()D", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf",
            "(D)Ljava/lang/Double;", false));
        return il;
    }

    private static InsnList blockFormat() {
        InsnList il = new InsnList();
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/util/Locale", "ROOT", "Ljava/util/Locale;"));
        il.add(new LdcInsnNode("Block: %d %d %d"));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(floorEntityCoord(0));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "getY", "()I", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(floorEntityCoord(2));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "format",
            "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false));
        return il;
    }

    private static InsnList chunkFormat() {
        InsnList il = new InsnList();
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/util/Locale", "ROOT", "Ljava/util/Locale;"));
        il.add(new LdcInsnNode("Chunk: %d %d %d"));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(floorEntityCoordDiv(0));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "getY", "()I", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/core/SectionPos", "blockToSectionCoord",
            "(I)I", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(floorEntityCoordDiv(2));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "format",
            "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false));
        return il;
    }

    private static InsnList facingFormat() {
        InsnList il = new InsnList();
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/util/Locale", "ROOT", "Ljava/util/Locale;"));
        il.add(new LdcInsnNode("Facing: %s (%s) (%.1f / %.1f)"));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 10));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "getYRot", "()F", false));
        il.add(new InsnNode(Opcodes.F2L));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/util/Mth", "wrapDegrees", "(J)F", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "getXRot", "()F", false));
        il.add(new InsnNode(Opcodes.F2L));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/util/Mth", "wrapDegrees", "(J)F", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/String", "format",
            "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false));
        return il;
    }

    private static InsnList dimensionFormat() {
        InsnList il = new InsnList();
        // minecraft.level.dimension().identifier() + " FC: " + chunks.size()
        il.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/Minecraft", "level",
            "Lnet/minecraft/client/multiplayer/ClientLevel;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/multiplayer/ClientLevel", "dimension",
            "()Lnet/minecraft/resources/ResourceKey;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/resources/ResourceKey", "identifier",
            "()Lnet/minecraft/resources/Identifier;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false));
        il.add(new LdcInsnNode(" FC: "));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/LongSet", "size", "()I", true));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(I)Ljava/lang/StringBuilder;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
            "()Ljava/lang/String;", false));
        return il;
    }

    // (long) Math.floor(entity.getX/Y/Z())
    private static InsnList floorEntityCoord(int axis) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity",
            axis == 0 ? "getX" : axis == 1 ? "getY" : "getZ", "()D", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false));
        il.add(new InsnNode(Opcodes.D2L));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
        return il;
    }

    // (long) Math.floor(entity.getX/Z() / 16.0)
    private static InsnList floorEntityCoordDiv(int axis) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity",
            axis == 0 ? "getX" : "getZ", "()D", false));
        il.add(new LdcInsnNode(16.0));
        il.add(new InsnNode(Opcodes.DDIV));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false));
        il.add(new InsnNode(Opcodes.D2L));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
        return il;
    }
}
