package com.farlands.g1.patch;

import org.objectweb.asm.ClassWriter;

/**
 * {@link ClassWriter} that falls back to {@code java/lang/Object} when a
 * common superclass cannot be resolved. The patcher runs on a classpath that
 * deliberately does not include Minecraft or fastutil classes, so frame
 * merges involving them must degrade gracefully. A less precise merged type
 * is always verifier-safe.
 */
public final class SafeClassWriter extends ClassWriter {

    public SafeClassWriter(int flags) {
        super(flags);
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (RuntimeException | LinkageError e) {
            return "java/lang/Object";
        }
    }
}
