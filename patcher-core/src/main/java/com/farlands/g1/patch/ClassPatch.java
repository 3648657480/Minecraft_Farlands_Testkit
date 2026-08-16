package com.farlands.g1.patch;

/**
 * A single bytecode patch applied to one or more Minecraft classes.
 * Implementations contain only patch logic written from scratch by the
 * FarLands project — never redistributed Minecraft source code.
 */
public interface ClassPatch {

    /** @return true if this patch applies to the given internal class name */
    boolean matches(String internalName);

    /**
     * Transforms the original class bytes. Implementations must not modify the input array.
     *
     * @param original the pristine class bytes from the user-supplied Minecraft jar
     * @return patched bytes, or {@code original} if the patch is not applicable
     */
    byte[] apply(byte[] original);

    /** Short name used in patch reports. */
    default String describe(String internalName) {
        return getClass().getSimpleName();
    }
}
