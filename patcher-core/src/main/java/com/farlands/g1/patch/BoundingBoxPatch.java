package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Sanitizes {@code BoundingBox} outputs at extreme coordinates.
 *
 * <p>Structure boxes near +/-2^31 hold wrapped values whose spans, centers
 * and accessors overflow 32-bit arithmetic and poison structure placement.
 * This patch keeps the int storage but recomputes every value that leaves
 * the class in the long domain and clamps it, mirroring the behavior of the
 * working fork build:</p>
 * <ul>
 *   <li>{@code minX/minZ/maxX/maxZ()} clamp to +/-2117483647 (2^31 - 30M)</li>
 *   <li>{@code getXSpan/getYSpan/getZSpan()} compute in long, clamp to [1, 256]</li>
 *   <li>{@code getCenter()} computes in long, clamps x/z to +/-2147467263
 *       (2^31 - 16384) and y to [-64, 320]</li>
 *   <li>{@code getLength()} computes in long</li>
 * </ul>
 */
public final class BoundingBoxPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/levelgen/structure/BoundingBox";
    private static final long CLAMP_XZ = 2_147_483_647L - 30_000_000L; // 2117483647
    private static final long CLAMP_CENTER = 2_147_483_647L - 16_384L; // 2147467263

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode minX = find(node, "minX", "()I");
        if (minX != null && containsClamp(minX)) {
            return original; // already patched
        }

        rewrite(node, "minX", "()I", clampGetter("minX"));
        rewrite(node, "minZ", "()I", clampGetter("minZ"));
        rewrite(node, "maxX", "()I", clampGetter("maxX"));
        rewrite(node, "maxZ", "()I", clampGetter("maxZ"));
        rewrite(node, "getXSpan", "()I", span("maxX", "minX"));
        rewrite(node, "getYSpan", "()I", span("maxY", "minY"));
        rewrite(node, "getZSpan", "()I", span("maxZ", "minZ"));
        rewrite(node, "getCenter", "()Lnet/minecraft/core/BlockPos;", center());
        rewrite(node, "getLength", "()Lnet/minecraft/core/Vec3i;", length());

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

    private static boolean containsClamp(MethodNode m) {
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode min && "java/lang/Math".equals(min.owner) && "clamp".equals(min.name)) {
                return true;
            }
        }
        return false;
    }

    private static InsnList clampGetter(String field) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(-2_117_483_648));
        il.add(new LdcInsnNode(2_117_483_647));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "clamp", "(JII)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList span(String maxField, String minField) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, maxField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, minField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new IntInsnNode(Opcodes.SIPUSH, 256));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "clamp", "(JII)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList center() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/core/BlockPos"));
        il.add(new InsnNode(Opcodes.DUP));
        centerAxis(il, "minX", "maxX");
        centerAxisY(il);
        centerAxis(il, "minZ", "maxZ");
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/core/BlockPos", "<init>", "(III)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static void centerAxis(InsnList il, String minField, String maxField) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, minField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, maxField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, minField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(2L));
        il.add(new InsnNode(Opcodes.LDIV));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(-CLAMP_CENTER - 1));
        il.add(new LdcInsnNode(CLAMP_CENTER));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "clamp", "(JJJ)J", false));
        il.add(new InsnNode(Opcodes.L2I));
    }

    private static void centerAxisY(InsnList il) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "minY", "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "maxY", "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "minY", "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(2L));
        il.add(new InsnNode(Opcodes.LDIV));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new IntInsnNode(Opcodes.BIPUSH, -64));
        il.add(new IntInsnNode(Opcodes.SIPUSH, 320));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "clamp", "(JII)I", false));
    }

    private static InsnList length() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/core/Vec3i"));
        il.add(new InsnNode(Opcodes.DUP));
        lengthAxis(il, "maxX", "minX");
        lengthAxis(il, "maxY", "minY");
        lengthAxis(il, "maxZ", "minZ");
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/core/Vec3i", "<init>", "(III)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static void lengthAxis(InsnList il, String maxField, String minField) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, maxField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, minField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.L2I));
    }
}
