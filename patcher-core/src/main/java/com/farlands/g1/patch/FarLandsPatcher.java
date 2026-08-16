package com.farlands.g1.patch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Applies all registered {@link ClassPatch}es to a Minecraft client jar.
 *
 * <p>This tool never contains or redistributes Minecraft code. It only reads
 * class bytes from a jar the user provides (their own copy of the game) and
 * rewrites them in place according to the patch set below.</p>
 */
public final class FarLandsPatcher {

    public static final String VERSION = "1.0.0";

    private final List<ClassPatch> patches = new ArrayList<>();

    public FarLandsPatcher register(ClassPatch patch) {
        patches.add(patch);
        return this;
    }

    public static FarLandsPatcher createDefault() {
        FarLandsPatcher p = new FarLandsPatcher();
        // 路线图隔离：每条线一个开关，只有通过交界点验收才并入默认构建。
        //   A 稳定线（默认）: 仅稳定性修复 + 访问器
        //   B 容器宽化线: -Dfarlands.wide
        //   D 生成连续性线: -Dfarlands.continuity
        boolean wide = Boolean.getBoolean("farlands.wide");
        boolean continuity = Boolean.getBoolean("farlands.continuity");
        if (continuity && !wide) {
            throw new IllegalArgumentException("farlands.continuity 依赖 farlands.wide（B 线容器宽化）");
        }
        p.register(continuity ? FunctionContextRealPatch.global() : FunctionContextRealPatch.noiseOnly());
        p.register(new Vec3iPatch());
        if (wide) {
            p.register(new Vec3iWidePatch());
            p.register(new BlockPosPatch());
            p.register(new ChunkPosPatch());
            p.register(new SectionPosPatch());
            p.register(new NoiseChunkPatch());
        }
        if (continuity) {
            p.register(new AquiferContextPatch());
            p.register(new AquiferLatticePatch());
        }
        p.register(new GsuPatch());
        p.register(new AabbClipPatch());
        p.register(new BlockCollisionsPatch());
        p.register(new BoundingBoxPatch());
        p.register(new ClientChunkCachePatch());
        p.register(new ClientChunkCacheStoragePatch());
        p.register(new ViewAreaPatch());
        p.register(new DebugEntryPositionPatch());
        p.register(new SectionOcclusionGraphPatch());
        p.register(new WgrPatch());
        return p;
    }

    public List<ClassPatch> patches() {
        return Collections.unmodifiableList(patches);
    }

    public byte[] patchClass(String internalName, byte[] original) {
        byte[] current = original;
        for (ClassPatch patch : patches) {
            if (patch.matches(internalName)) {
                current = patch.apply(current);
            }
        }
        return current;
    }

    /** Patches a class and returns the name of the patch that changed it (null if unchanged). */
    public String patchClassAndDescribe(String internalName, byte[] original, byte[][] output) {
        byte[] current = original;
        String changedBy = null;
        for (ClassPatch patch : patches) {
            if (patch.matches(internalName)) {
                byte[] out = patch.apply(current);
                if (out != current) {
                    changedBy = patch.describe(internalName);
                }
                current = out;
            }
        }
        output[0] = current;
        return changedBy;
    }

    /**
     * Reads {@code input} jar, applies patches, writes {@code output} jar.
     *
     * @return a report of how many classes were patched
     */
    public PatchReport patchJar(Path input, Path output) throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        int patchedClasses = 0;
        int totalClasses = 0;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(input));
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(output))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (isSignatureEntry(name)) {
                    zis.closeEntry();
                    continue; // drop jar signature files: modified bytes would fail digest checks
                }
                zos.putNextEntry(new ZipEntry(name));
                byte[] data = zis.readAllBytes();
                if (name.endsWith(".class")) {
                    totalClasses++;
                    String internal = name.substring(0, name.length() - ".class".length());
                    byte[][] outHolder = new byte[1][];
                    String changedBy = patchClassAndDescribe(internal, data, outHolder);
                    byte[] out = outHolder[0];
                    if (changedBy != null) {
                        patchedClasses++;
                        counts.merge(changedBy, 1, Integer::sum);
                    }
                    zos.write(out);
                } else {
                    zos.write(data);
                }
                zos.closeEntry();
                zis.closeEntry();
            }
        }
        return new PatchReport(totalClasses, patchedClasses, counts);
    }

    private static boolean isSignatureEntry(String name) {
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC");
    }

    public record PatchReport(int totalClasses, int patchedClasses, Map<String, Integer> patchCounts) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Scanned ").append(totalClasses).append(" classes, patched ").append(patchedClasses).append(":\n");
            patchCounts.forEach((k, v) -> sb.append("  - ").append(k).append(": ").append(v).append('\n'));
            return sb.toString();
        }
    }
}
