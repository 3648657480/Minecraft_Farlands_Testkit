package com.farlands.g1.cli;

import com.farlands.g1.patch.FarLandsPatcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standalone patcher CLI.
 *
 * <p>Usage:</p>
 * <pre>
 *   java -jar farlands-patcher.jar --in &lt;minecraft-client.jar&gt; --out &lt;patched.jar&gt; [--verbose]
 * </pre>
 *
 * <p>The input jar must be the user's own, legitimately obtained copy of the
 * Minecraft client. This tool never downloads, embeds, or redistributes
 * Minecraft content; it only rewrites class bytes on the user's machine.</p>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Path in = null;
        Path out = null;
        boolean verbose = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--in" -> in = Paths.get(args[++i]);
                case "--out" -> out = Paths.get(args[++i]);
                case "--verbose" -> verbose = true;
                case "--help", "-h" -> {
                    usage();
                    return;
                }
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    usage();
                    System.exit(2);
                }
            }
        }

        if (in == null || out == null) {
            usage();
            System.exit(2);
        }
        if (!Files.isRegularFile(in)) {
            System.err.println("Input jar not found: " + in);
            System.exit(1);
        }
        if (Files.exists(out)) {
            System.err.println("Output file already exists: " + out);
            System.exit(1);
        }

        System.out.println("FarLands Patcher v" + FarLandsPatcher.VERSION);
        System.out.println("Input : " + in.toAbsolutePath());
        System.out.println("Output: " + out.toAbsolutePath());

        FarLandsPatcher patcher = FarLandsPatcher.createDefault();
        FarLandsPatcher.PatchReport report = patcher.patchJar(in, out);
        System.out.println(report);
        System.out.println("Done. Install the patched jar alongside the FarLands mod.");
    }

    private static void usage() {
        System.out.println("""
            Usage: farlands-patcher --in <minecraft-client.jar> --out <patched.jar> [--verbose]

            The input jar must be your own copy of the Minecraft client (26.2).
            The output jar replaces it in your launcher profile; the FarLands mod
            (farlands-g1-mod) must be installed in the mods folder.""");
    }
}
