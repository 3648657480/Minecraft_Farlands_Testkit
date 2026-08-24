package farlands.translator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * E2: offline save translator for 26.2 worlds.
 *
 * <p>Shifts every chunk (region + entities), the level.dat respawn point
 * and the player positions by a chunk delta, so the whole save lives in a
 * new epoch. This is the offline step that lets the epoch re-center between
 * sessions without any runtime re-keying.</p>
 *
 * <p>Run: gradlew :mod:translateSave -Pworld=&lt;world&gt; -Pdx=&lt;chunks&gt; -Pdz=&lt;chunks&gt;
 * or: gradlew :mod:translateSave -Pworld=&lt;world&gt; -Pdx=-verify -Pdz=&lt;regionFile&gt;</p>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        if (args.length < 2) {
            System.out.println("usage: Main <world> <dx> <dz>   |   Main <world> -verify <regionFile>");
            return;
        }
        if (args[1].equals("-verify")) {
            verifyRegion(Path.of(args[0]).resolve("dimensions/minecraft/overworld/region"), Path.of(args[2]));
            return;
        }
        if (args.length < 3) {
            System.out.println("usage: Main <world> <dx> <dz>");
            return;
        }
        Path world = Path.of(args[0]);
        int dx = Integer.parseInt(args[1]);
        int dz = Integer.parseInt(args[2]);
        Path overworld = world.resolve("dimensions/minecraft/overworld");

        int shifted = 0;
        shifted += translateRegionDir(overworld.resolve("region"), dx, dz);
        shifted += translateRegionDir(overworld.resolve("entities"), dx, dz);
        shiftLevelDat(world, dx, dz);
        shiftPlayerData(world, dx, dz);
        System.out.println("translated " + shifted + " chunks (+" + dx + "," + dz + " chunks)");
    }

    private static int translateRegionDir(Path dir, int dx, int dz) throws Exception {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int count = 0;
        List<Path> mcaFiles = Files.list(dir).filter(p -> p.toString().endsWith(".mca")).sorted().toList();
        for (Path mca : mcaFiles) {
            count += translateRegionFile(dir, mca, dx, dz);
        }
        return count;
    }

    private static int translateRegionFile(Path dir, Path mca, int dx, int dz) throws Exception {
        RegionStorageInfo info = new RegionStorageInfo("translate",
            net.minecraft.world.level.Level.OVERWORLD, "region");
        RegionFileStorage source = new RegionFileStorage(info, dir, false);
        Map<ChunkPos, CompoundTag> chunks = new TreeMap<>(Comparator.comparingLong(ChunkPos::pack));
        int[] region = regionCoords(mca);
        ChunkPos min = new ChunkPos(region[0] << 5, region[1] << 5);
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                ChunkPos pos = new ChunkPos(min.x() + x, min.z() + z);
                CompoundTag tag = source.read(pos);
                if (tag != null) {
                    chunks.put(pos, tag);
                }
            }
        }
        source.close();

        if (chunks.isEmpty()) {
            return 0;
        }

        Map<ChunkPos, CompoundTag> byDest = new TreeMap<>(Comparator.comparingLong(ChunkPos::pack));
        for (Map.Entry<ChunkPos, CompoundTag> e : chunks.entrySet()) {
            ChunkPos dest = new ChunkPos(e.getKey().x() + dx, e.getKey().z() + dz);
            shiftChunkTag(e.getValue(), dx, dz);
            byDest.put(dest, e.getValue());
        }

        Map<Long, RegionFileStorage> sinks = new TreeMap<>();
        for (Map.Entry<ChunkPos, CompoundTag> e : byDest.entrySet()) {
            ChunkPos pos = e.getKey();
            long regionKey = ChunkPos.pack(pos.getRegionX(), pos.getRegionZ());
            RegionFileStorage sink = sinks.computeIfAbsent(regionKey,
                k -> new RegionFileStorage(info, dir, false));
            sink.write(pos, e.getValue());
        }
        for (RegionFileStorage sink : sinks.values()) {
            sink.close();
        }

        Files.deleteIfExists(mca);
        return chunks.size();
    }

    private static int[] regionCoords(Path mca) {
        String name = mca.getFileName().toString(); // r.<rx>.<rz>.mca
        String[] parts = name.substring(2, name.length() - 4).split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    private static void shiftChunkTag(CompoundTag tag, int dx, int dz) {
        tag.putInt("xPos", tag.getIntOr("xPos", 0) + dx);
        tag.putInt("zPos", tag.getIntOr("zPos", 0) + dz);
        ListTag entities = tag.getListOrEmpty("Entities");
        for (int i = 0; i < entities.size(); i++) {
            entities.getCompound(i).ifPresent(c -> shiftEntity(c, dx, dz));
        }
        ListTag blockEntities = tag.getListOrEmpty("block_entities");
        for (int i = 0; i < blockEntities.size(); i++) {
            blockEntities.getCompound(i).ifPresent(be -> {
                if (be.contains("x") && be.contains("z")) {
                    be.putInt("x", be.getIntOr("x", 0) + dx * 16);
                    be.putInt("z", be.getIntOr("z", 0) + dz * 16);
                }
            });
        }
    }

    private static void shiftEntity(CompoundTag entity, int dx, int dz) {
        if (entity.contains("Pos") && entity.get("Pos") instanceof ListTag pos) {
            pos.set(0, DoubleTag.valueOf(pos.getDoubleOr(0, 0.0) + dx * 16.0));
            pos.set(2, DoubleTag.valueOf(pos.getDoubleOr(2, 0.0) + dz * 16.0));
        }
    }

    private static void shiftLevelDat(Path world, int dx, int dz) throws Exception {
        Path levelDat = world.resolve("level.dat");
        if (!Files.exists(levelDat)) {
            return;
        }
        CompoundTag nbt = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
        System.out.println("  level.dat root keys: " + nbt.keySet());
        CompoundTag data = nbt.getCompound("Data").orElse(new CompoundTag());
        System.out.println("  level.dat Data keys: " + data.keySet());
        System.out.println("  level.dat Player present: " + data.contains("Player"));
        if (data.contains("Player")) {
            CompoundTag player = data.getCompoundOrEmpty("Player");
            System.out.println("  level.dat Player keys: " + player.keySet());
            if (player.contains("Pos")) {
                System.out.println("  level.dat Player Pos: " + player.get("Pos"));
            }
        }
        System.out.println("  level.dat spawn before: " + data.get("spawn"));
        if (data.contains("SpawnX")) {
            data.putInt("SpawnX", data.getIntOr("SpawnX", 0) + dx * 16);
            data.putInt("SpawnZ", data.getIntOr("SpawnZ", 0) + dz * 16);
        }
        if (data.contains("RespawnData") && data.get("RespawnData") instanceof CompoundTag rd) {
            if (rd.contains("pos") && rd.get("pos") instanceof ListTag pos) {
                pos.set(0, IntTag.valueOf(pos.getIntOr(0, 0) + dx * 16));
                pos.set(2, IntTag.valueOf(pos.getIntOr(2, 0) + dz * 16));
            }
        }
        // 26.2: the respawn lives in the "spawn" compound (RespawnData codec).
        if (data.contains("spawn") && data.get("spawn") instanceof CompoundTag spawn) {
            if (spawn.get("pos") instanceof net.minecraft.nbt.IntArrayTag arr) {
                int[] vals = arr.getAsIntArray();
                if (vals.length >= 3) {
                    vals[0] += dx * 16;
                    vals[2] += dz * 16;
                    spawn.put("pos", new net.minecraft.nbt.IntArrayTag(vals));
                }
            } else if (spawn.get("pos") instanceof ListTag pos) {
                pos.set(0, IntTag.valueOf(pos.getIntOr(0, 0) + dx * 16));
                pos.set(2, IntTag.valueOf(pos.getIntOr(2, 0) + dz * 16));
            }
        }
        nbt.put("Data", data);
        NbtIo.writeCompressed(nbt, levelDat);
        System.out.println("  level.dat respawn shifted");
    }

    private static void shiftPlayerData(Path world, int dx, int dz) throws Exception {
        Path players = world.resolve("players");
        if (!Files.isDirectory(players)) {
            return;
        }
        for (Path f : Files.list(players).toList()) {
            if (!Files.isRegularFile(f)) {
                continue;
            }
            CompoundTag nbt = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            if (nbt.contains("Pos") && nbt.get("Pos") instanceof ListTag pos) {
                pos.set(0, DoubleTag.valueOf(pos.getDoubleOr(0, 0.0) + dx * 16.0));
                pos.set(2, DoubleTag.valueOf(pos.getDoubleOr(2, 0.0) + dz * 16.0));
            }
            NbtIo.writeCompressed(nbt, f);
            System.out.println("  player " + f.getFileName() + " shifted");
        }
    }

    private static void verifyRegion(Path worldRegionDir, Path regionFile) throws Exception {
        RegionStorageInfo info = new RegionStorageInfo("verify",
            net.minecraft.world.level.Level.OVERWORLD, "region");
        RegionFileStorage src = new RegionFileStorage(info, worldRegionDir, false);
        int[] reg = regionCoords(regionFile);
        ChunkPos min = new ChunkPos(reg[0] << 5, reg[1] << 5);
        int found = 0;
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                ChunkPos pos = new ChunkPos(min.x() + x, min.z() + z);
                CompoundTag tag = src.read(pos);
                if (tag != null) {
                    System.out.println("chunk (" + pos.x() + "," + pos.z() + ") xPos="
                        + tag.getIntOr("xPos", -1) + " zPos=" + tag.getIntOr("zPos", -1)
                        + " status=" + tag.getString("Status"));
                    found++;
                }
            }
        }
        System.out.println("total " + found + " chunks in " + regionFile.getFileName());
        src.close();
    }
}
