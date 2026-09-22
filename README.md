# FarLands G1

> [中文说明](README.zh-CN.md) | **English**

Minecraft 26.2 Far Lands toolset: real-coordinate exploration up to 2^63
(eventually 1e306) - no scaling, no fake coordinates, real terrain.

**Player guides**: [English](docs/USAGE.en.md) | [中文](docs/USAGE.md)

## What this project is (and is not)

This repository contains **only original code**: an ASM patcher library, a
standalone CLI, and a Fabric mod with runtime support (mixins + helpers). It
contains **no Minecraft source code, no decompiled output, and no game files**.

The patcher reads class bytes from a Minecraft client jar **you supply
yourself** (your own legitimately obtained copy, downloaded from Mojang) and
rewrites them in place on your machine.

## Features

- **Playable past the 2^31 boundary**: continuous terrain, working AABB,
  selection, breaking and placing (E4)
- **Arbitrary-distance travel**: `/realtp` with real coordinates (works in
  command blocks), archive-style relocation - the current epoch is archived,
  the target epoch restored, the middle is never generated (E5)
- **BigInteger exact coordinates**: the epoch is stored exactly and parsed from
  arbitrary-precision strings (`/realtp @p 1e1000 100 0` works)
- **F3 real-coordinate display**: XYZ / Block / Chunk show real coordinates and
  switch to exact BigInteger values beyond double precision; extra lines show
  `Local (in-epoch)`, `Epoch ... Laps (2^31)` and the current `Real double ULP`
  (quantization step); the noise readouts sample at the real coordinates
- **Automatic world configuration**: a fresh world auto-creates
  `world/farlands.properties` (epoch = origin) - live out of the box, no JVM
  flags
- **Fluid tick rate limit**: guards against the recursive fluid-tick explosion
  found by stress testing

## Requirements

- Java 25 (the same runtime Minecraft 26.2 uses)
- The official Minecraft 26.2 client jar, obtained by yourself

## Build

```
gradlew clean build
```

Produces `patcher-cli/build/libs/patcher-cli-1.0-SNAPSHOT.jar` and
`mod/build/libs/farlands-g1-mod-1.0-SNAPSHOT.jar`.

## Install

1. Patch your own client jar:
```powershell
java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" "-Dfarlands.epoch=true" `
  -jar patcher-cli-1.0-SNAPSHOT.jar --in <your-26.2.jar> --out <fork.jar>
```
2. Use the fork jar as the version jar (back up the original)
3. Put the mod jar into `mods/`
4. Launch - no JVM flags needed

See [docs/USAGE.en.md](docs/USAGE.en.md) for the full player guide.

## In-game

```
/realtp <x> <y> <z>                teleport yourself using real coordinates
/realtp <targets> <x> <y> <z>      teleport entities (@p/@e/...; command blocks OK)
```

Out-of-window targets trigger an archive relocation: the current epoch's chunks
move to `world/farlands_epochs/`, the target epoch's archive is restored if it
exists, and the middle is simply never generated. The player lands at local
origin of the new epoch - same real coordinates, seamless terrain.

## Distance phenomena

Measured in this project's pipeline. All values are **a specific result under
specific experimental conditions** (this pipeline, its version and positions);
they are not a correction of any external record - see R10 in
[docs/USAGE.en.md](docs/USAGE.en.md).

| Real coordinate | Phenomenon (this project's conditions) |
|---|---|
| 2^53 (9.007e15) | Symptom onset: 1-block sampling quantizes to 2-block pairs (underground subtle, surface normal) |
| 2^55 (3.603e16) | Main-terrain quantization: regular 8-block steps/stripes ("cheese") |
| 2^56 (7.206e16) | Plate structures + tiled water surface (16 blocks) |
| 2^63 (9.223e18) | 128-chunk homogeneous mosaic |
| ~1.80876436895e24 | Measured onset of the surface main-terrain change (strip/wall structures) |
| >2.43e27 | Terrain not terminated under these conditions (highly repetitive) |
| 1.8e308 | Water-column world (horizontal collapse + normal vertical) |

The far-domain degradation is a **cascade**: different noise components fail at
different thresholds (underground first, surface main terrain later), so there
is no single "starting point".

Phenomenon zones are sightseeing areas - the geometry is extremely heavy.

## Documentation

- [docs/USAGE.en.md](docs/USAGE.en.md) / [docs/USAGE.md](docs/USAGE.md) - player guides
- [docs/CONFIG.en.md](docs/CONFIG.en.md) / [docs/CONFIG.md](docs/CONFIG.md) - configuration reference + presets
- [docs/EXPERIMENTS.md](docs/EXPERIMENTS.md) - experiment protocol, F0 results, far-domain phenomenon table
- [docs/REVIEW.md](docs/REVIEW.md) - architecture review (coordinate domains, mechanisms)
- [docs/ROADMAP.md](docs/ROADMAP.md) - milestones and lessons
- [docs/WORKFLOW.md](docs/WORKFLOW.md) - build/test/deploy workflow
- [docs/E-LINE-DESIGN.md](docs/E-LINE-DESIGN.md) - E line design notes

## License

MIT. This project ships no Minecraft assets; all Minecraft code is patched
locally on the user's machine from the user's own copy.
