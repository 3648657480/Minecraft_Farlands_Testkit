package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Fixes the aquifer lattice size computation at the +/-2^31 seam.
 *
 * <p>{@code Aquifer$NoiseBasedAquifer.<init>} computes
 * {@code gridSizeX = gridX(maxBlockX-5) - gridX(minBlockX-5) + 1} with int
 * arithmetic. For chunks whose block coordinates straddle the int wrap the
 * two grid values carry opposite signs, producing a huge/negative span.
 * The following multiplication
 * {@code gridSizeX * gridSizeY * gridSizeZ} overflows int and routinely
 * lands on a giant positive value, so every aquifer at the seam allocates
 * a multi-hundred-MB {@code FluidStatus[]} (measured: 97 arrays = 7.5 GB
 * of transient garbage, OOM'ing the game).</p>
 *
 * <p>The fix rewrites only {@code gridSizeX}/{@code gridSizeZ} from the wide
 * chunk coordinates ({@code ChunkPos.xLong()/zLong()} - requires the B line
 * container widening). {@code minGridX}/{@code minGridZ} intentionally keep
 * their vanilla (wrapped) values so the lattice indexing in
 * {@code computeSubstance}/{@code getIndex}, which also computes grid
 * coordinates from wrapped ints, stays self-consistent.</p>
 */
public final class AquiferLatticePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer";
    private static final String CTOR_DESC =
        "(Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/ChunkPos;"
            + "Lnet/minecraft/world/level/levelgen/NoiseRouter;"
            + "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;II"
            + "Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;)V";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        boolean changed = false;
        for (MethodNode m : node.methods) {
            if (!"<init>".equals(m.name) || !CTOR_DESC.equals(m.desc)) {
                continue;
            }
            changed |= fixGridSize(m, "gridSizeX", "xLong");
            changed |= fixGridSize(m, "gridSizeZ", "zLong");
        }
        if (!changed) {
            return original;
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * After the vanilla {@code putfield gridSize*} instruction, re-computes the
     * field from wide coordinates: real span is always 3 grid cells for one
     * chunk. New locals start at 15 (vanilla uses up to 14).
     */
    private static boolean fixGridSize(MethodNode m, String field, String axisAccessor) {
        FieldInsnNode put = null;
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD
                && TARGET.equals(fi.owner) && field.equals(fi.name)) {
                put = fi;
                break;
            }
        }
        if (put == null) {
            return false;
        }

        int minLocal = 15;
        int maxLocal = 16;
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", axisAccessor, "()J", false));
        il.add(new LdcInsnNode(4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new LdcInsnNode(5L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, minLocal));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", axisAccessor, "()J", false));
        il.add(new LdcInsnNode(4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new LdcInsnNode(15L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(5L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ISTORE, maxLocal));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, maxLocal));
        il.add(new VarInsnNode(Opcodes.ILOAD, minLocal));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, field, "I"));
        if (put.getNext() == null) {
            m.instructions.add(il);
        } else {
            m.instructions.insert(put.getNext(), il);
        }
        return true;
    }

    @Override
    public String describe(String internalName) {
        return "AquiferLatticePatch (gridSizeX/Z wrap-safe at 2^31)";
    }
}
