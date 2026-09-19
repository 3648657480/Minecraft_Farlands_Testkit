package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * E line: marks the epoch-capable jar.
 *
 * <p>The epoch design keeps every engine domain (chunk keys, sections,
 * blocks, generation cells) in the LOCAL domain: when the epoch is set,
 * all ChunkPos instances are constructed with local coordinates, so the
 * int view is the field value itself (no rebasing needed). The REAL domain
 * lives in exactly three places: {@code FarProjection.epochBlockX/Z} (the
 * constant origin), the generation unwrap (origin + local cells) and the
 * F3 display. Rebase attempts broke this: view rebasing double-shifted
 * already-local fields (observed as acquireGeneration NPE / chunk key
 * mismatches once the epoch engaged).</p>
 *
 * <p>This patch only adds a {@code farlands$epoch} marker field so the mod
 * can detect the epoch-capable jar state.</p>
 */
public final class ChunkPosEpochPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/ChunkPos";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (node.fields.stream().noneMatch(f -> "x".equals(f.name) && "J".equals(f.desc))) {
            return original; // requires the B-line widening
        }
        if (node.fields.stream().noneMatch(f -> "farlands$epoch".equals(f.name))) {
            node.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "farlands$epoch", "I", null, 1));
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    @Override
    public String describe(String internalName) {
        return "ChunkPosEpochPatch (marker only, local-domain views)";
    }
}
