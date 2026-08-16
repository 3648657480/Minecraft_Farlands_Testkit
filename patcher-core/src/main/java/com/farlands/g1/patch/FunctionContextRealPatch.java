package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;

/**
 * Redirect of {@code FunctionContext.blockX/Y/Z()I + i2d} call sites to the
 * {@code getBlockX/Y/ZDouble()D} accessors.
 *
 * <p>The noise pipeline implements {@code FunctionContext} with int
 * coordinates that wrap at +/-2^31, tearing the terrain apart along a
 * one-chunk-wide seam (the "lava river"). The FarLands mod adds double
 * accessors whose default implementation preserves vanilla behavior
 * ({@code (double) blockX()}), while {@code NoiseChunk} overrides them with
 * unsigned, wrap-continuous real coordinates.</p>
 *
 * <p>Only call sites whose result is immediately converted with {@code i2d}
 * are rewritten; int-domain consumers are left untouched. Two scopes are
 * available: {@link #noiseOnly()} (the working fork's two classes, zero
 * behavior change elsewhere) and {@link #global()} (every density class,
 * which makes terrain continuous across 2^31 but costs more at runtime).</p>
 */
public final class FunctionContextRealPatch implements ClassPatch {

    private static final String CONTEXT = "net/minecraft/world/level/levelgen/DensityFunction$FunctionContext";

    private final Set<String> targets;

    private FunctionContextRealPatch(Set<String> targets) {
        this.targets = targets;
    }

    /** The two noise classes rewritten by the working fork build. */
    public static FunctionContextRealPatch noiseOnly() {
        return new FunctionContextRealPatch(Set.of(
            "net/minecraft/world/level/levelgen/DensityFunctions$Noise",
            "net/minecraft/world/level/levelgen/DensityFunctions$ShiftedNoise"));
    }

    /** Every class (seam fix across the whole density pipeline). */
    public static FunctionContextRealPatch global() {
        return new FunctionContextRealPatch(null);
    }

    @Override
    public boolean matches(String internalName) {
        return targets == null || targets.contains(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        boolean[] found = {false};
        ClassReader cr = new ClassReader(original);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                        if (isContextGet(opcode, owner, method, descriptor, itf)) {
                            found[0] = true;
                        }
                        super.visitMethodInsn(opcode, owner, method, descriptor, itf);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!found[0]) {
            return original;
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    /** pending {blockX,blockY,blockZ} call whose result is not yet consumed */
                    private String pending = null;

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                        if (isContextGet(opcode, owner, method, descriptor, itf)) {
                            flushPending();
                            pending = method;
                            return;
                        }
                        flushPending();
                        super.visitMethodInsn(opcode, owner, method, descriptor, itf);
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (pending != null) {
                            if (opcode == Opcodes.I2D) {
                                String replacement = switch (pending) {
                                    case "blockX" -> "getBlockXDouble";
                                    case "blockY" -> "getBlockYDouble";
                                    default -> "getBlockZDouble";
                                };
                                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, CONTEXT, replacement, "()D", true);
                                pending = null;
                                return;
                            }
                            flushPending();
                        }
                        super.visitInsn(opcode);
                    }

                    @Override
                    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                        flushPending();
                        super.visitFrame(type, numLocal, local, numStack, stack);
                    }

                    @Override
                    public void visitLabel(org.objectweb.asm.Label label) {
                        flushPending();
                        super.visitLabel(label);
                    }

                    @Override
                    public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
                        flushPending();
                        super.visitJumpInsn(opcode, label);
                    }

                    @Override
                    public void visitVarInsn(int opcode, int varIndex) {
                        flushPending();
                        super.visitVarInsn(opcode, varIndex);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        flushPending();
                        super.visitFieldInsn(opcode, owner, name, descriptor);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        flushPending();
                        super.visitLdcInsn(value);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        flushPending();
                        super.visitTypeInsn(opcode, type);
                    }

                    @Override
                    public void visitIincInsn(int varIndex, int increment) {
                        flushPending();
                        super.visitIincInsn(varIndex, increment);
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        flushPending();
                        super.visitIntInsn(opcode, operand);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor,
                            org.objectweb.asm.Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                        flushPending();
                        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                    }

                    @Override
                    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                        flushPending();
                        super.visitMultiANewArrayInsn(descriptor, numDimensions);
                    }

                    @Override
                    public void visitLookupSwitchInsn(org.objectweb.asm.Label dflt, int[] keys, org.objectweb.asm.Label[] labels) {
                        flushPending();
                        super.visitLookupSwitchInsn(dflt, keys, labels);
                    }

                    @Override
                    public void visitTableSwitchInsn(int min, int max, org.objectweb.asm.Label dflt, org.objectweb.asm.Label... labels) {
                        flushPending();
                        super.visitTableSwitchInsn(min, max, dflt, labels);
                    }

                    @Override
                    public void visitMaxs(int maxStack, int maxLocals) {
                        flushPending();
                        super.visitMaxs(maxStack, maxLocals);
                    }

                    private void flushPending() {
                        if (pending == null) {
                            return;
                        }
                        super.visitMethodInsn(Opcodes.INVOKEINTERFACE, CONTEXT, pending, "()I", true);
                        pending = null;
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }

    private static boolean isContextGet(int opcode, String owner, String method, String descriptor, boolean itf) {
        return opcode == Opcodes.INVOKEINTERFACE && itf && CONTEXT.equals(owner) && "()I".equals(descriptor)
            && (method.equals("blockX") || method.equals("blockY") || method.equals("blockZ"));
    }

    @Override
    public String describe(String internalName) {
        return "FunctionContextRealPatch";
    }
}
