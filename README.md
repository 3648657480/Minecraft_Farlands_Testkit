# FarLands G1

> [中文说明](README.zh-CN.md) | **English**
> Version 1.0.0 (authority: repo-root VERSION)

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
- **Configuration at world creation**: three FarLands tabs on the create-world
  screen (FarLands / Terrain / Test) set the epoch, terrain policy and
  headless test-harness keys before any terrain is generated; new worlds
  inherit a global template (see [CONFIG](docs/CONFIG.en.md))
- **Fluid tick rate limit**: guards against the recursive fluid-tick explosion
  found by stress testing

## Requirements

- Java 25 (the same runtime Minecraft 26.2 uses)
- The official Minecraft 26.2 client jar, obtained by yourself

## Build

```
gradlew clean build
```

Produces `patcher-cli/build/libs/patcher-cli-1.0.0.jar` and
`mod/build/libs/farlands-g1-mod-1.0.0.jar`.

## Install

1. Patch your own client jar with `patcher-cli-1.0.0.jar`; use the fork jar as the version jar (back up the original).
2. Put `farlands-g1-mod-1.0.0.jar` into `mods/` and launch.
3. Set epoch and terrain options on the FarLands tabs when creating a world.
   Full walkthrough: [docs/USAGE.en.md](docs/USAGE.en.md).

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

Measurements, method and conditions live in
[docs/EXPERIMENTS.md](docs/EXPERIMENTS.md). They are specific results under
specific experimental conditions and do not correct any external record - see
the red lines in [AGENTS.md](AGENTS.md) (R10).

## Documentation

- [docs/USAGE.en.md](docs/USAGE.en.md) / [docs/USAGE.md](docs/USAGE.md) - player guides (install, `/realtp`, configuration entry point, safety)
- [docs/CONFIG.en.md](docs/CONFIG.en.md) / [docs/CONFIG.md](docs/CONFIG.md) - configuration reference + presets
- [docs/EXPERIMENTS.md](docs/EXPERIMENTS.md) - experiment protocol, F0 results, far-domain phenomenon table
- [docs/REVIEW.md](docs/REVIEW.md) - architecture review (coordinate domains, mechanisms)
- [docs/ROADMAP.md](docs/ROADMAP.md) - milestones and lessons
- [docs/WORKFLOW.md](docs/WORKFLOW.md) - build/test/deploy workflow
- [docs/UNLOCK-DESIGN.md](docs/UNLOCK-DESIGN.md) - unlock-mode design note (opt-in lab tool, not in the default build)
- [AGENTS.md](AGENTS.md) - binding red lines (R7-R10) and the five iron rules
- [docs/archive/](docs/archive/) - historical design notes (E line, wide containers)

## License

MIT. This project ships no Minecraft assets; all Minecraft code is patched
locally on the user's machine from the user's own copy.
