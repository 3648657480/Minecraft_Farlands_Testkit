package farlands.worlddiff;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F0 evidence tool: compares the overworld chunk contents of two worlds
 * (same seed expected) chunk by chunk.
 *
 * <p>Prints a report with: seed of both worlds, per-chunk verdicts for every
 * chunk present in both, counts of chunks missing on either side, per-side
 * combined content hashes (SHA-256 over canonical NBT), and a final VERDICT
 * line. Exit code 0 = IDENTICAL, 1 = DIFFERENT, 2 = usage/error.</p>
 *
 * <p>Run: gradlew :mod:worldDiff -PworldA=&lt;world&gt; -PworldB=&lt;world&gt; [-Preport=&lt;file&gt;]</p>
 */
public final class Main {

    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int MAX_DIFF_PATHS_PER_CHUNK = 30;
    private static final int MAX_LISTED_CHUNKS = 40;
    /**
     * Volatile metadata excluded from both the comparison and the content
     * hash: LastUpdate is the game tick when the chunk was last touched and
     * varies with save timing; it carries no terrain information.
     */
    private static final java.util.Set<String> IGNORED_KEYS = java.util.Set.of("LastUpdate");

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        if (args.length < 2) {
            System.out.println("usage: Main <worldA> <worldB> [reportFile]");
            System.exit(2);
            return;
        }
        Path worldA = Path.of(args[0]);
        Path worldB = Path.of(args[1]);
        Path reportPath = args.length > 2 ? Path.of(args[2]) : null;
        String dim = System.getProperty("farlands.worlddiff.dim", "overworld");

        List<String> report = new ArrayList<>();
        int exit = compare(worldA, worldB, dim, report);

        String text = String.join(System.lineSeparator(), report);
        System.out.println(text);
        if (reportPath != null) {
            if (reportPath.getParent() != null) {
                Files.createDirectories(reportPath.getParent());
            }
            Files.writeString(reportPath, text + System.lineSeparator(), StandardCharsets.UTF_8);
        }
        System.exit(exit);
    }

    private static int compare(Path worldA, Path worldB, String dim, List<String> report) throws Exception {
        Path regionA = regionDir(worldA, dim);
        Path regionB = regionDir(worldB, dim);
        report.add("worldA: " + worldA);
        report.add("worldB: " + worldB);
        report.add("dimension: " + dim);
        report.add("ignored keys: " + IGNORED_KEYS + " (volatile metadata, not terrain content)");
        report.add("regionA: " + (regionA != null ? regionA : "MISSING"));
        report.add("regionB: " + (regionB != null ? regionB : "MISSING"));
        if (regionA == null || regionB == null) {
            report.add("VERDICT: ERROR (region directory missing)");
            return 2;
        }

        String seedA = readSeed(worldA);
        String seedB = readSeed(worldB);
        report.add("seedA: " + seedA);
        report.add("seedB: " + seedB);
        if (!seedA.equals(seedB)) {
            report.add("WARNING: seeds differ - the comparison is NOT a valid control");
        }
        report.add("spawnA: " + readSpawn(worldA));
        report.add("spawnB: " + readSpawn(worldB));

        TreeSet<Long> regions = new TreeSet<>();
        collectRegions(regionA, regions);
        collectRegions(regionB, regions);
        report.add("regions: " + regions.size());

        RegionStorageInfo info = new RegionStorageInfo("worlddiff", dimKey(dim), "region");
        MessageDigest digestA = MessageDigest.getInstance("SHA-256");
        MessageDigest digestB = MessageDigest.getInstance("SHA-256");

        int compared = 0;
        int identical = 0;
        int differing = 0;
        int notFull = 0;
        int onlyInA = 0;
        int onlyInB = 0;
        int onlyInAFull = 0;
        int onlyInBFull = 0;
        List<String> diffChunks = new ArrayList<>();
        List<String> missingChunks = new ArrayList<>();
        List<String> notFullChunks = new ArrayList<>();

        try (RegionFileStorage storageA = new RegionFileStorage(info, regionA, false);
             RegionFileStorage storageB = new RegionFileStorage(info, regionB, false)) {
            for (long regionKey : regions) {
                int rx = ChunkPos.getX(regionKey);
                int rz = ChunkPos.getZ(regionKey);
                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {
                        ChunkPos pos = new ChunkPos((rx << 5) + x, (rz << 5) + z);
                        CompoundTag tagA = readChunk(storageA, pos);
                        CompoundTag tagB = readChunk(storageB, pos);
                        if (tagA == null && tagB == null) {
                            continue;
                        }
                        if (tagA == null) {
                            onlyInB++;
                            if ("minecraft:full".equals(status(tagB))) {
                                onlyInBFull++;
                            }
                            if (missingChunks.size() < MAX_LISTED_CHUNKS) {
                                missingChunks.add("only in B: (" + pos.x() + "," + pos.z() + ") status=" + status(tagB));
                            }
                            continue;
                        }
                        if (tagB == null) {
                            onlyInA++;
                            if ("minecraft:full".equals(status(tagA))) {
                                onlyInAFull++;
                            }
                            if (missingChunks.size() < MAX_LISTED_CHUNKS) {
                                missingChunks.add("only in A: (" + pos.x() + "," + pos.z() + ") status=" + status(tagA));
                            }
                            continue;
                        }
                        String statusA = status(tagA);
                        String statusB = status(tagB);
                        if (!"minecraft:full".equals(statusA) || !"minecraft:full".equals(statusB)) {
                            notFull++;
                            if (notFullChunks.size() < MAX_LISTED_CHUNKS) {
                                notFullChunks.add("(" + pos.x() + "," + pos.z() + "): A=" + statusA + " B=" + statusB);
                            }
                            continue;
                        }
                        compared++;
                        digestA.update(canonical(tagA));
                        digestB.update(canonical(tagB));
                        List<String> diffs = new ArrayList<>();
                        compareTag("", tagA, tagB, diffs, MAX_DIFF_PATHS_PER_CHUNK);
                        if (diffs.isEmpty()) {
                            identical++;
                        } else {
                            differing++;
                            if (diffChunks.size() < MAX_LISTED_CHUNKS) {
                                diffChunks.add("chunk (" + pos.x() + "," + pos.z() + "): "
                                    + String.join(" | ", diffs));
                            }
                        }
                    }
                }
            }
        }

        report.add("compared (full/full): " + compared + "  identical: " + identical + "  differing: " + differing);
        report.add("not full on at least one side (excluded from verdict): " + notFull);
        report.add("only in A: " + onlyInA + " (full: " + onlyInAFull + ")  only in B: " + onlyInB
            + " (full: " + onlyInBFull + ")");
        report.add("combined hash A: " + hex(digestA.digest()));
        report.add("combined hash B: " + hex(digestB.digest()));
        if (!diffChunks.isEmpty()) {
            report.add("--- differing chunks (first " + diffChunks.size() + ") ---");
            report.addAll(diffChunks);
        }
        if (!missingChunks.isEmpty()) {
            report.add("--- missing chunks (first " + missingChunks.size() + ") ---");
            report.addAll(missingChunks);
        }
        if (!notFullChunks.isEmpty()) {
            report.add("--- not-full chunks (first " + notFullChunks.size() + ") ---");
            report.addAll(notFullChunks);
        }
        boolean ok = differing == 0;
        report.add("VERDICT: " + (ok ? "IDENTICAL" : "DIFFERENT"));
        return ok ? 0 : 1;
    }

    private static CompoundTag readChunk(RegionFileStorage storage, ChunkPos pos) {
        try {
            return storage.read(pos);
        } catch (Exception e) {
            return null;
        }
    }

    private static String dimFolder(String dim) {
        return switch (dim) {
            case "the_end", "end", "ender" -> "the_end";
            case "the_nether", "nether" -> "the_nether";
            default -> "overworld";
        };
    }

    private static net.minecraft.resources.ResourceKey<Level> dimKey(String dim) {
        return switch (dim) {
            case "the_end", "end", "ender" -> Level.END;
            case "the_nether", "nether" -> Level.NETHER;
            default -> Level.OVERWORLD;
        };
    }

    private static Path regionDir(Path world, String dim) {
        Path modern = world.resolve("dimensions/minecraft/" + dimFolder(dim) + "/region");
        if (Files.isDirectory(modern)) {
            return modern;
        }
        if ("overworld".equals(dimFolder(dim))) {
            Path legacy = world.resolve("region");
            if (Files.isDirectory(legacy)) {
                return legacy;
            }
        }
        return null;
    }

    private static void collectRegions(Path dir, TreeSet<Long> out) throws Exception {
        try (var stream = Files.list(dir)) {
            for (Path p : stream.toList()) {
                Matcher m = REGION_FILE.matcher(p.getFileName().toString());
                if (m.matches()) {
                    out.add(ChunkPos.pack(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
                }
            }
        }
    }

    private static String readSeed(Path world) {
        // 26.2: world gen settings live in a SavedData file, not level.dat.
        Path savedData = world.resolve("data/minecraft/world_gen_settings.dat");
        if (Files.exists(savedData)) {
            try {
                CompoundTag nbt = NbtIo.readCompressed(savedData, NbtAccounter.unlimitedHeap());
                if (nbt.contains("seed")) {
                    return String.valueOf(nbt.getLongOr("seed", Long.MIN_VALUE));
                }
                for (String key : nbt.keySet()) {
                    Tag inner = nbt.get(key);
                    if (inner instanceof CompoundTag c && c.contains("seed")) {
                        return String.valueOf(c.getLongOr("seed", Long.MIN_VALUE)) + " (under " + key + ")";
                    }
                }
                return "unknown (root keys: " + nbt.keySet() + ")";
            } catch (Exception e) {
                return "error: " + e;
            }
        }
        Path levelDat = world.resolve("level.dat");
        if (!Files.exists(levelDat)) {
            return "no seed source";
        }
        try {
            CompoundTag nbt = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
            CompoundTag data = nbt.getCompound("Data").orElse(null);
            if (data == null) {
                return "unknown (no Data)";
            }
            CompoundTag wgs = data.getCompound("WorldGenSettings").orElse(null);
            if (wgs != null && wgs.contains("seed")) {
                return String.valueOf(wgs.getLongOr("seed", Long.MIN_VALUE));
            }
            if (data.contains("RandomSeed")) {
                return String.valueOf(data.getLongOr("RandomSeed", Long.MIN_VALUE));
            }
            return "unknown (Data keys: " + data.keySet() + ")";
        } catch (Exception e) {
            return "error: " + e;
        }
    }

    /** Chunk generation status (e.g. {@code minecraft:full}), or null. */
    private static String status(CompoundTag tag) {
        return tag.contains("Status") ? tag.getStringOr("Status", "?") : null;
    }

    /** World spawn block position from level.dat ({@code Data.spawn.pos}). */
    private static String readSpawn(Path world) {
        Path levelDat = world.resolve("level.dat");
        if (!Files.exists(levelDat)) {
            return "no level.dat";
        }
        try {
            CompoundTag nbt = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
            CompoundTag data = nbt.getCompound("Data").orElse(null);
            if (data == null) {
                return "no Data";
            }
            Tag spawn = data.get("spawn");
            if (spawn instanceof CompoundTag spawnTag) {
                Tag pos = spawnTag.get("pos");
                if (pos instanceof IntArrayTag arr) {
                    int[] v = arr.getAsIntArray();
                    if (v.length >= 3) {
                        return "(" + v[0] + "," + v[1] + "," + v[2] + ")";
                    }
                } else if (pos instanceof ListTag list && list.size() >= 3) {
                    return "(" + list.getIntOr(0, 0) + "," + list.getIntOr(1, 0) + "," + list.getIntOr(2, 0) + ")";
                }
                return "spawn present, pos=" + pos;
            }
            return "no spawn tag";
        } catch (Exception e) {
            return "error: " + e.getClass().getSimpleName();
        }
    }

    private static void compareTag(String path, Tag a, Tag b, List<String> out, int max) {
        if (out.size() >= max) {
            return;
        }
        byte idA = a.getId();
        byte idB = b.getId();
        if (idA != idB) {
            out.add(path + ": type " + idA + " != " + idB);
            return;
        }
        switch (idA) {
            case 1, 2, 3, 4 -> {
                long va = ((NumericTag) a).longValue();
                long vb = ((NumericTag) b).longValue();
                if (va != vb) {
                    out.add(path + ": " + va + " != " + vb);
                }
            }
            case 5 -> {
                float va = ((FloatTag) a).floatValue();
                float vb = ((FloatTag) b).floatValue();
                if (Float.floatToIntBits(va) != Float.floatToIntBits(vb)) {
                    out.add(path + ": " + va + " != " + vb);
                }
            }
            case 6 -> {
                double va = ((DoubleTag) a).doubleValue();
                double vb = ((DoubleTag) b).doubleValue();
                if (Double.doubleToLongBits(va) != Double.doubleToLongBits(vb)) {
                    out.add(path + ": " + va + " != " + vb);
                }
            }
            case 7 -> {
                if (!Arrays.equals(((ByteArrayTag) a).getAsByteArray(), ((ByteArrayTag) b).getAsByteArray())) {
                    out.add(path + ": byte[] differs");
                }
            }
            case 8 -> {
                String va = ((StringTag) a).value();
                String vb = ((StringTag) b).value();
                if (!va.equals(vb)) {
                    out.add(path + ": '" + va + "' != '" + vb + "'");
                }
            }
            case 9 -> {
                ListTag la = (ListTag) a;
                ListTag lb = (ListTag) b;
                if (la.size() != lb.size()) {
                    out.add(path + ": list size " + la.size() + " != " + lb.size());
                    return;
                }
                for (int i = 0; i < la.size() && out.size() < max; i++) {
                    compareTag(path + "[" + i + "]", la.get(i), lb.get(i), out, max);
                }
            }
            case 10 -> {
                CompoundTag ca = (CompoundTag) a;
                CompoundTag cb = (CompoundTag) b;
                TreeSet<String> keys = new TreeSet<>(ca.keySet());
                keys.addAll(cb.keySet());
                for (String k : keys) {
                    if (IGNORED_KEYS.contains(k)) {
                        continue;
                    }
                    if (out.size() >= max) {
                        return;
                    }
                    Tag va = ca.get(k);
                    Tag vb = cb.get(k);
                    if (va == null) {
                        out.add(path + "." + k + ": missing in A");
                    } else if (vb == null) {
                        out.add(path + "." + k + ": missing in B");
                    } else {
                        compareTag(path.isEmpty() ? k : path + "." + k, va, vb, out, max);
                    }
                }
            }
            case 11 -> {
                if (!Arrays.equals(((IntArrayTag) a).getAsIntArray(), ((IntArrayTag) b).getAsIntArray())) {
                    out.add(path + ": int[] differs");
                }
            }
            case 12 -> {
                if (!Arrays.equals(((LongArrayTag) a).getAsLongArray(), ((LongArrayTag) b).getAsLongArray())) {
                    out.add(path + ": long[] differs");
                }
            }
            default -> {
                // EndTag: nothing to compare.
            }
        }
    }

    private static byte[] canonical(Tag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeCanonical(tag, out);
            out.flush();
            return bytes.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static void writeCanonical(Tag tag, DataOutputStream out) throws Exception {
        out.writeByte(tag.getId());
        switch (tag.getId()) {
            case 1, 2, 3, 4 -> out.writeLong(((NumericTag) tag).longValue());
            case 5 -> out.writeFloat(((FloatTag) tag).floatValue());
            case 6 -> out.writeDouble(((DoubleTag) tag).doubleValue());
            case 7 -> {
                byte[] v = ((ByteArrayTag) tag).getAsByteArray();
                out.writeInt(v.length);
                out.write(v);
            }
            case 8 -> out.writeUTF(((StringTag) tag).value());
            case 9 -> {
                ListTag list = (ListTag) tag;
                out.writeInt(list.size());
                for (int i = 0; i < list.size(); i++) {
                    writeCanonical(list.get(i), out);
                }
            }
            case 10 -> {
                CompoundTag compound = (CompoundTag) tag;
                TreeSet<String> keys = new TreeSet<>(compound.keySet());
                keys.removeAll(IGNORED_KEYS);
                out.writeInt(keys.size());
                for (String k : keys) {
                    out.writeUTF(k);
                    writeCanonical(compound.get(k), out);
                }
            }
            case 11 -> {
                int[] v = ((IntArrayTag) tag).getAsIntArray();
                out.writeInt(v.length);
                for (int i : v) {
                    out.writeInt(i);
                }
            }
            case 12 -> {
                long[] v = ((LongArrayTag) tag).getAsLongArray();
                out.writeInt(v.length);
                for (long l : v) {
                    out.writeLong(l);
                }
            }
            default -> {
            }
        }
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
