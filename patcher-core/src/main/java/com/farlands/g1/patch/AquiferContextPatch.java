package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.Set;

/**
 * Routes noise-pipeline sampling contexts through the unsigned-continuous
 * {@code RealContext} instead of {@code SinglePointContext}.
 *
 * <p>Several generation paths (aquifer cache fill, preliminary surface
 * level, base height) sample density functions through
 * {@code SinglePointContext} built from int coordinates that wrap at
 * +/-2^31, tearing the terrain at the seam. {@code RealContext} exposes the
 * same int values but reinterprets them as unsigned in its double
 * accessors, which the globally redirected density functions use - making
 * the sampled noise continuous without touching any grid math.</p>
 */
public final class AquiferContextPatch implements ClassPatch {

    private static final Set<String> TARGETS = Set.of(
        "net/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer",
        "net/minecraft/world/level/levelgen/NoiseChunk",
        // B line: biome climate noise (temperature/humidity/continentalness/...) is
        // sampled through a SinglePointContext built from the LOCAL block coords, so
        // biomes repeat with the local window at far coordinates. Routing it through
        // RealContext makes the biome noise use the real coordinate too.
        "net/minecraft/world/level/biome/Climate$Sampler",
        // B line: FindTopSurface rebuilds an inner context from context.blockX()/blockZ()
        // (int, local), throwing away the real coordinate the outer context carried.
        "net/minecraft/world/level/levelgen/DensityFunctions$FindTopSurface",
        // B line: the carver asks the aquifer for the substance at each carved block
        // through a SinglePointContext (local) -> cave fluid uses local noise.
        "net/minecraft/world/level/levelgen/carver/WorldCarver",
        // B line: The End's biome source samples erosion through a SinglePointContext
        // (local) built from the weird-section block coords.
        "net/minecraft/world/level/biome/TheEndBiomeSource"
    );
    private static final String FROM = "net/minecraft/world/level/levelgen/DensityFunction$SinglePointContext";
    private static final String TO = "com/farlands/g1/runtime/RealContext";

    @Override
    public boolean matches(String internalName) {
        return TARGETS.contains(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        boolean changed = false;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof TypeInsnNode t && t.getOpcode() == Opcodes.NEW && FROM.equals(t.desc)) {
                    t.desc = TO;
                    changed = true;
                } else if (insn instanceof org.objectweb.asm.tree.MethodInsnNode mi
                    && mi.getOpcode() == Opcodes.INVOKESPECIAL && FROM.equals(mi.owner)
                    && "<init>".equals(mi.name) && "(III)V".equals(mi.desc)) {
                    mi.owner = TO;
                    changed = true;
                } else if (insn instanceof org.objectweb.asm.tree.MethodInsnNode mi
                    && mi.getOpcode() == Opcodes.INVOKEINTERFACE
                    && "net/minecraft/world/level/levelgen/PositionalRandomFactory".equals(mi.owner)
                    && "at".equals(mi.name) && "(III)Lnet/minecraft/util/RandomSource;".equals(mi.desc)) {
                    mi.setOpcode(Opcodes.INVOKESTATIC);
                    mi.owner = "com/farlands/g1/runtime/FarAquiferHelper";
                    mi.itf = false;
                    mi.desc = "(Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;III)Lnet/minecraft/util/RandomSource;";
                    changed = true;
                }
            }
        }
        if (!changed) {
            return original;
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
