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
  arbitrary-precision strings (`/realtp @p 1e1000 100 0` works); F3 shows a
  `Real (exact)` line
- **Automatic world configuration**: a fresh world auto-creates
  `world/farlands.properties` (epoch = origin) - live out of the box, no JVM
  flags
- **Distance phenomena verified**: 2^53 terrain stop point, 2^63 far lands
  (2048-block homogeneous mosaic), 1.8e308 water-column world
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

| Real coordinate | Phenomenon |
|---|---|
| 2^53 (9.007e15) | Terrain stop point (ulp 2, adjacent samples merge) |
| 2^63 (9.223e18) | Far lands: 128-chunk homogeneous block mosaic |
| 1.8e308 | Water-column world (horizontal collapse + normal vertical) |

Phenomenon zones are sightseeing areas - the geometry is extremely heavy.

## Documentation

- [docs/USAGE.en.md](docs/USAGE.en.md) / [docs/USAGE.md](docs/USAGE.md) - player guides
- [docs/REVIEW.md](docs/REVIEW.md) - architecture review (coordinate domains, mechanisms)
- [docs/ROADMAP.md](docs/ROADMAP.md) - milestones and lessons
- [docs/WORKFLOW.md](docs/WORKFLOW.md) - build/test/deploy workflow
- [docs/E-LINE-DESIGN.md](docs/E-LINE-DESIGN.md) - E line design notes

## License

MIT. This project ships no Minecraft assets; all Minecraft code is patched
locally on the user's machine from the user's own copy.
