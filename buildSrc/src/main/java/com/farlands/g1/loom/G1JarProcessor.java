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

    public static final class Spec implements MinecraftJarProcessor.Spec {
    }

    @Override
    public String getName() {
        return "farlands-g1";
    }

    @Override
    public Spec buildSpec(SpecContext context) {
        return new Spec();
    }

    @Override
    public void processJar(Path jar, Spec spec, ProcessorContext context) throws IOException {
        Path tmp = jar.resolveSibling(jar.getFileName() + ".g1tmp");
        Files.deleteIfExists(tmp);
        FarLandsPatcher patcher = FarLandsPatcher.createDefault();
        FarLandsPatcher.PatchReport report = patcher.patchJar(jar, tmp);
        Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("[FarLands-G1] " + report);
    }
}
