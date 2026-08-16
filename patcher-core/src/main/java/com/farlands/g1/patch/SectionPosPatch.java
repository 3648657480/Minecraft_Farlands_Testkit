package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Repacks {@code SectionPos.asLong} from 22+22+20 bits to 27+27+10 bits
 * (x: bits 37-63, z: bits 10-36, y: bits 0-9).
 *
 * <p>The packed key is only produced and consumed through the SectionPos
 * accessors, so the layout change is transparent to the whole game. It
 * extends the section-key addressing from 2^22 sections (67M blocks) to
 * 2^26 sections (1.07G blocks), and aligns the key wrap exactly with the
 * +/-2^31 block boundary of the int domain. Y keeps 10 bits (+/-512
 * sections, far beyond any world height).</p>
 */
public final class SectionPosPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/core/SectionPos";
    private static final long X_MASK = 0x7FFFFFFL; // 27 bits
    private static final long Y_MASK = 0x3FFL;     // 10 bits

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        // publish the widened wrap so the mod can adapt its epoch math
        if (node.fields.stream().noneMatch(f -> "farlands$wrap".equals(f.name))) {
            node.fields.add(new org.objectweb.asm.tree.FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "farlands$wrap", "I", null, 134217728));
        }

        rewrite(node, "asLong", "(III)J", pack());        rewrite(node, "x", "(J)I", new InsnList() {{
            add(new VarInsnNode(Opcodes.LLOAD, 0));
            add(new IntInsnNode(Opcodes.BIPUSH, 37));
            add(new InsnNode(Opcodes.LSHR));
            add(new InsnNode(Opcodes.L2I));
            add(new InsnNode(Opcodes.IRETURN));
        }});
        rewrite(node, "z", "(J)I", new InsnList() {{
            add(new VarInsnNode(Opcodes.LLOAD, 0));
            add(new IntInsnNode(Opcodes.BIPUSH, 27));
            add(new InsnNode(Opcodes.LSHL));
            add(new IntInsnNode(Opcodes.BIPUSH, 37));
            add(new InsnNode(Opcodes.LSHR));
            add(new InsnNode(Opcodes.L2I));
            add(new InsnNode(Opcodes.IRETURN));
        }});
        rewrite(node, "y", "(J)I", new InsnList() {{
            add(new VarInsnNode(Opcodes.LLOAD, 0));
            add(new IntInsnNode(Opcodes.BIPUSH, 54));
            add(new InsnNode(Opcodes.LSHL));
            add(new IntInsnNode(Opcodes.BIPUSH, 54));
            add(new InsnNode(Opcodes.LSHR));
            add(new InsnNode(Opcodes.L2I));
            add(new InsnNode(Opcodes.IRETURN));
        }});
        // column key: zero the y bits (bits 0-9 in the widened layout)
        rewrite(node, "getZeroNode", "(J)J", new InsnList() {{
            add(new VarInsnNode(Opcodes.LLOAD, 0));
            add(new LdcInsnNode(-1024L));
            add(new InsnNode(Opcodes.LAND));
            add(new InsnNode(Opcodes.LRETURN));
        }});

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static InsnList pack() {
        InsnList il = new InsnList();
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(new VarInsnNode(Opcodes.LSTORE, 3));
        // x << 37
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(X_MASK));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 37));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new InsnNode(Opcodes.LOR));
        il.add(new VarInsnNode(Opcodes.LSTORE, 3));
        // y << 0
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(Y_MASK));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new InsnNode(Opcodes.LOR));
        il.add(new VarInsnNode(Opcodes.LSTORE, 3));
        // z << 10
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(X_MASK));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 10));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new InsnNode(Opcodes.LOR));
        il.add(new VarInsnNode(Opcodes.LSTORE, 3));
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new InsnNode(Opcodes.LRETURN));
        return il;
    }

    private static void rewrite(ClassNode node, String name, String desc, InsnList body) {
        MethodNode m = null;
        for (MethodNode candidate : node.methods) {
            if (name.equals(candidate.name) && desc.equals(candidate.desc)) {
                m = candidate;
                break;
            }
        }
        if (m == null) {
            throw new IllegalStateException(TARGET + "#" + name + desc + " not found");
        }
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body);
    }
}
