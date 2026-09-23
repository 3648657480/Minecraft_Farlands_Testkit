package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * UNLOCK (opt-in lab tool, NOT in the default patch set).
 *
 * <p>Raises the vanilla caps of the render-distance and simulation-distance
 * options (vanilla max = 32, or 16 on low-memory presets) to {@link #UNLOCK_MAX}
 * by replacing the max argument of the {@code OptionInstance.IntRange(IIZ)}
 * constructor for the two option instances whose key string is
 * {@code options.renderDistance} / {@code options.simulationDistance}.</p>
 *
 * <p>This only lifts the slider/option cap. Beyond it there is no guard of any
 * kind: rendering, chunk generation, memory and GPU load all scale together.
 * Enabling it requires {@code -Dfarlands.unlock} AND
 * {@code -Dfarlands.unlock.i_know_what_im_doing} (「我知道我在做什么」).</p>
 *
 * <p>Vertex-count limits are a separate matter: raising the Java constant does
 * not help under OpenGL (the driver still caps indices); only the Vulkan
 * backend truly lifts it. See {@code docs/UNLOCK-DESIGN.md}.</p>
 */
public final class OptionsUnlockPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/client/Options";
    private static final String INTRANGE = "net/minecraft/client/OptionInstance$IntRange";
    private static final String[] TARGET_OPTIONS = {
        "options.renderDistance",
        "options.simulationDistance"
    };

    /** Hard cap. Not "unlimited" on purpose. */
    public static final int UNLOCK_MAX = 96;

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
            changed |= rewrite(m);
        }
        if (!changed) {
            return original;
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * For every {@code IntRange(IIZ)} construction whose option key is one of the
     * distance options, replace the max argument. Stack before the constructor
     * call is {@code [objref, min, max, bool]}; we rewrite it to
     * {@code [objref, min, UNLOCK_MAX, bool]}.
     */
    private static boolean rewrite(MethodNode m) {
        boolean changed = false;
        String lastString = null;
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
                lastString = s;
            }
            if (insn instanceof MethodInsnNode mi
                && mi.getOpcode() == Opcodes.INVOKESPECIAL
                && INTRANGE.equals(mi.owner)
                && "<init>".equals(mi.name)
                && "(IIZ)V".equals(mi.desc)
                && isTarget(lastString)) {
                int tmp = m.maxLocals;
                m.maxLocals += 1;
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ISTORE, tmp)); // pop bool
                il.add(new InsnNode(Opcodes.POP));            // pop vanilla max
                il.add(new LdcInsnNode(UNLOCK_MAX));
                il.add(new VarInsnNode(Opcodes.ILOAD, tmp));  // re-push bool
                m.instructions.insertBefore(insn, il);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isTarget(String s) {
        if (s == null) {
            return false;
        }
        for (String t : TARGET_OPTIONS) {
            if (t.equals(s)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe(String internalName) {
        return "OptionsUnlockPatch (render/simulation distance max -> " + UNLOCK_MAX + ")";
    }
}
