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
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ListIterator;

/**
 * Makes the collision-iterator box tests use "real" coordinates.
 *
 * <p>{@code BlockCollisions.computeNext()} tests {@code box.intersects(...)}
 * and moves voxel shapes by the block position. Below -2^31 the int
 * coordinates wrap negative while the entity box stores the true unsigned
 * value, so every test fails and entities fall through the world. This patch
 * routes the x/z arguments through a private {@code real(int)} helper
 * (unsigned conversion below -1e8) exactly like the working fork build.</p>
 */
public final class BlockCollisionsPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/BlockCollisions";
    private static final String AABB = "net/minecraft/world/phys/AABB";
    private static final String VOXEL_SHAPE = "net/minecraft/world/phys/shapes/VoxelShape";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (node.methods.stream().anyMatch(m -> "real".equals(m.name))) {
            return original;
        }
        node.methods.add(realMethod());

        MethodNode computeNext = null;
        for (MethodNode m : node.methods) {
            if ("computeNext".equals(m.name)) {
                computeNext = m;
                break;
            }
        }
        if (computeNext == null) {
            throw new IllegalStateException(TARGET + "#computeNext not found");
        }
        patchComputeNext(computeNext);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static void patchComputeNext(MethodNode mn) {
        ListIterator<AbstractInsnNode> it = mn.instructions.iterator();
        while (it.hasNext()) {
            AbstractInsnNode insn = it.next();
            if (!(insn instanceof MethodInsnNode min) || min.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                continue;
            }
            if (AABB.equals(min.owner) && "intersects".equals(min.name) && "(DDDDDD)Z".equals(min.desc)) {
                patchIntersectsArgs(mn, min);
            } else if (VOXEL_SHAPE.equals(min.owner) && "move".equals(min.name)
                && "(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/shapes/VoxelShape;".equals(min.desc)) {
                patchMoveArgs(mn, min);
            }
        }
    }

    private static void patchIntersectsArgs(MethodNode mn, MethodInsnNode invoke) {
        // Expected 18-instruction argument sequence directly before the invoke,
        // walking backwards from the invoke (opcode, local var or -1):
        int[][] expected = {
            {Opcodes.DADD, -1}, {Opcodes.DCONST_1, -1}, {Opcodes.I2D, -1}, {Opcodes.ILOAD, 3},
            {Opcodes.DADD, -1}, {Opcodes.DCONST_1, -1}, {Opcodes.I2D, -1}, {Opcodes.ILOAD, 2},
            {Opcodes.DADD, -1}, {Opcodes.DCONST_1, -1}, {Opcodes.I2D, -1}, {Opcodes.ILOAD, 1},
            {Opcodes.I2D, -1}, {Opcodes.ILOAD, 3},
            {Opcodes.I2D, -1}, {Opcodes.ILOAD, 2},
            {Opcodes.I2D, -1}, {Opcodes.ILOAD, 1},
        };
        AbstractInsnNode cur = invoke.getPrevious();
        java.util.ArrayList<AbstractInsnNode> remove = new java.util.ArrayList<>();
        for (int pos = 0; pos < expected.length; pos++) {
            if (cur == null) {
                throw new IllegalStateException(TARGET + ": unexpected intersects arg pattern at " + pos);
            }
            int op = expected[pos][0];
            int var = expected[pos][1];
            boolean ok = cur.getOpcode() == op
                && (var < 0 || (cur instanceof VarInsnNode v && v.var == var));
            if (!ok) {
                System.err.println(TARGET + ": pattern mismatch at pos " + pos
                    + " expected op=" + op + " var=" + var + " found op=" + cur.getOpcode()
                    + " class=" + cur.getClass().getSimpleName());
                AbstractInsnNode dbg = invoke;
                StringBuilder sb = new StringBuilder("walk-back: ");
                for (int d = 0; d < 22 && dbg != null; d++) {
                    sb.append(dbg.getOpcode()).append(' ');
                    dbg = dbg.getPrevious();
                }
                System.err.println(sb);
                throw new IllegalStateException(TARGET + ": unexpected intersects arg pattern at " + pos
                    + " (op=" + cur.getOpcode() + ")");
            }
            remove.add(cur);
            cur = cur.getPrevious();
        }
        InsnList repl = new InsnList();
        repl.add(new VarInsnNode(Opcodes.ILOAD, 1));
        repl.add(realXCall());
        repl.add(new VarInsnNode(Opcodes.ILOAD, 2));
        repl.add(new InsnNode(Opcodes.I2D));
        repl.add(new VarInsnNode(Opcodes.ILOAD, 3));
        repl.add(realZCall());
        repl.add(new VarInsnNode(Opcodes.ILOAD, 1));
        repl.add(realXCall());
        repl.add(new InsnNode(Opcodes.DCONST_1));
        repl.add(new InsnNode(Opcodes.DADD));
        repl.add(new VarInsnNode(Opcodes.ILOAD, 2));
        repl.add(new InsnNode(Opcodes.I2D));
        repl.add(new InsnNode(Opcodes.DCONST_1));
        repl.add(new InsnNode(Opcodes.DADD));
        repl.add(new VarInsnNode(Opcodes.ILOAD, 3));
        repl.add(realZCall());
        repl.add(new InsnNode(Opcodes.DCONST_1));
        repl.add(new InsnNode(Opcodes.DADD));
        mn.instructions.insertBefore(invoke, repl);
        for (AbstractInsnNode n : remove) {
            mn.instructions.remove(n);
        }
    }

    private static void patchMoveArgs(MethodNode mn, MethodInsnNode invoke) {
        // Expected: ALOAD 0, GETFIELD pos, then the invoke.
        AbstractInsnNode getfield = invoke.getPrevious();
        AbstractInsnNode aload = getfield.getPrevious();
        if (!(getfield instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD
                && "pos".equals(f.name))
            || !(aload instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == 0)) {
            throw new IllegalStateException(TARGET + ": unexpected move arg pattern");
        }
        InsnList repl = new InsnList();
        repl.add(new VarInsnNode(Opcodes.ILOAD, 1));
        repl.add(realXCall());
        repl.add(new VarInsnNode(Opcodes.ILOAD, 2));
        repl.add(new InsnNode(Opcodes.I2D));
        repl.add(new VarInsnNode(Opcodes.ILOAD, 3));
        repl.add(realZCall());
        mn.instructions.insertBefore(invoke, repl);
        mn.instructions.remove(getfield);
        mn.instructions.remove(aload);
        invoke.desc = "(DDD)Lnet/minecraft/world/phys/shapes/VoxelShape;";
    }

    private static MethodInsnNode realXCall() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, "com/farlands/g1/util/FarProjection", "unwrapX", "(I)D", false);
    }

    private static MethodInsnNode realZCall() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, "com/farlands/g1/util/FarProjection", "unwrapZ", "(I)D", false);
    }

    private static MethodNode realMethod() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "real", "(I)D", null, null);
        LabelNode plain = new LabelNode();
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new LdcInsnNode(-100_000_000));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, plain));
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "toUnsignedLong", "(I)J", false));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new InsnNode(Opcodes.DRETURN));
        il.add(plain);
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new InsnNode(Opcodes.I2D));
        il.add(new InsnNode(Opcodes.DRETURN));
        return mn;
    }
}
