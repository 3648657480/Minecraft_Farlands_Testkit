package com.farlands.g1.loom;

import com.farlands.g1.patch.FarLandsPatcher;
import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loom build-time processor: applies the FarLands patch set to the Minecraft
 * jar while Loom prepares it, so development runs and mod compilation use the
 * patched game automatically.
 *
 * <p>Register with:</p>
 * <pre>
 * loom {
 *     addMinecraftJarProcessor(com.farlands.g1.loom.G1JarProcessor)
 * }
 * </pre>
 */
public class G1JarProcessor implements MinecraftJarProcessor<G1JarProcessor.Spec> {

    /**
     * Cache key: Loom re-processes the Minecraft jar only when this spec
     * changes, so the patch flags must be part of it. An empty spec made
     * dev runs reuse a stale jar across different -Dfarlands.* flags.
     */
    public static final class Spec implements MinecraftJarProcessor.Spec {

        private final boolean wide;
        private final boolean continuity;
        private final boolean epoch;
        private final boolean unlock;

        public Spec(boolean wide, boolean continuity, boolean epoch, boolean unlock) {
            this.wide = wide;
            this.continuity = continuity;
            this.epoch = epoch;
            this.unlock = unlock;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Spec s)) {
                return false;
            }
            return wide == s.wide && continuity == s.continuity && epoch == s.epoch
                && unlock == s.unlock;
        }

        @Override
        public int hashCode() {
            return (wide ? 1 : 0) | (continuity ? 2 : 0) | (epoch ? 4 : 0) | (unlock ? 8 : 0);
        }

        @Override
        public String toString() {
            return "wide=" + wide + " continuity=" + continuity + " epoch=" + epoch
                + " unlock=" + unlock;
        }
    }

    @Override
    public String getName() {
        return "farlands-g1";
    }

    @Override
    public Spec buildSpec(SpecContext context) {
        return new Spec(
            Boolean.getBoolean("farlands.wide"),
            Boolean.getBoolean("farlands.continuity"),
            Boolean.getBoolean("farlands.epoch"),
            FarLandsPatcher.unlockEnabled());
    }

    @Override
    public void processJar(Path jar, Spec spec, ProcessorContext context) throws IOException {
        Path tmp = jar.resolveSibling(jar.getFileName() + ".g1tmp");
        Files.deleteIfExists(tmp);
        FarLandsPatcher patcher = FarLandsPatcher.createDefault(spec.wide, spec.continuity, spec.epoch, spec.unlock);
        FarLandsPatcher.PatchReport report = patcher.patchJar(jar, tmp);
        Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("[FarLands-G1] " + report);
    }
}
