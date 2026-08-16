package com.farlands.g1.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;

/**
 * Redirects {@code FunctionContext.blockX/Y/Z()I + i2d} call sites to the
 * {@code getBlockX/Y/ZDouble()D} default methods provided by the FarLands mod.
 *
 * <p>At extreme coordinates the 32-bit block position overflows. Reading the
 * coordinate as a double through the extended accessor keeps the noise
 * functions (which run entirely in double domain) correct beyond 2^31.</p>
 */
public final class DensityNoisePatch implements ClassPatch {

    private static final String CONTEXT = "net/minecraft/world/level/levelgen/DensityFunction$FunctionContext";
    private static final Set<String> TARGETS = Set.of(
        "net/minecraft/world/level/levelgen/DensityFunctions$Noise",
        "net/minecraft/world/level/levelgen/DensityFunctions$ShiftedNoise"
    );

    @Override
    public boolean matches(String internalName) {
        return TARGETS.contains(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    boolean skipI2D = false;

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKEINTERFACE && isInterface
                            && CONTEXT.equals(owner) && "()I".equals(descriptor)) {
                            String replacement = switch (method) {
                                case "blockX" -> "getBlockXDouble";
                                case "blockY" -> "getBlockYDouble";
                                case "blockZ" -> "getBlockZDouble";
                                default -> null;
                            };
                            if (replacement != null) {
                                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, replacement, "()D", true);
                                skipI2D = true;
                                return;
                            }
                        }
                        super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (skipI2D && opcode == Opcodes.I2D) {
                            skipI2D = false;
                            return;
                        }
                        skipI2D = false;
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }
}
