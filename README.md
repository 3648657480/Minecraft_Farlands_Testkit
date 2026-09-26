# FarLands G1

> [中文说明](README.zh-CN.md) | **English**
> Version: see repo-root `VERSION` (the single authority)

> **1.0.0 has a fatal far-domain crash - upgrade to the latest version** (see
> [CHANGELOG.md](CHANGELOG.md)). If your startup banner shows `1.0.0`, update.

> **Status**: G1 is **paused as a "playable / studyable" release** - the
> epoch / local-domain route has reached its design boundary.

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

- **Java 21 or newer (25 recommended)**. If `java -version` shows `1.8`, `java -jar` fails with
  `UnsupportedClassVersionError` - the bundled `patch.bat` auto-picks Java 21+.
- Your own legitimately obtained **Minecraft 26.2 client jar** (a named/mojmap version jar, e.g. the
  Fabric `...\versions\26.2-Fabric 0.19.3\26.2-Fabric 0.19.3.jar`).

## Download (recommended - one file, no build needed)

Grab the **latest** bundle `FarLands-G1-<version>.zip` from
[Releases](https://github.com/3648657480/Minecraft_Farlands_Testkit/releases). **One download, it contains**:

- `patcher-cli-<version>.jar` - the command-line patcher for your own client jar
- `farlands-g1-mod-<version>.jar` - the Fabric mod
- `docs/` (player guides) + `INSTALL.md` (one-page install) + `README` + `LICENSE`

Unzip and follow `INSTALL.md`. **You do not need to build this project.**

## Before you start (read this)

1. **Version must match**: the patch targets **Minecraft 26.2 + Fabric Loader 0.19.3**; use `26.2-Fabric 0.19.3`.
2. **You replace the version jar**: rename the patched `..._fork.jar` to the original version jar's name and
   **replace the original** (back the original up first).
3. **Disable your launcher's "file/integrity verification"**: the jar is rewritten and no longer Mojang-signed;
   with verification on it is flagged as corrupt or auto-"repaired" (wiping the patch).
4. **Prefer launching offline (do not sign in with a premium account)**: a modified client is incompatible with
   the premium auth/integrity flow. (Supply your own legitimate game copy.)
5. **Expected**: the patcher drops the 2 jar signature files, so the output has 2 fewer entries; all other entries
   (lang, textures, directories) are preserved as-is.

## Install

1. **Back up**: copy the version's `26.2.jar` to `26.2.jar.bak`.
2. **Patch**: run the bundled **`patch.bat`** (drag your client jar onto it; it auto-picks Java 21+ and
   handles paths), or run `patcher-cli-<version>.jar` manually - see
   [docs/USAGE.en.md](docs/USAGE.en.md) §1.1 for the command and path notes.
3. **Replace**: use the fork jar as that version's jar.
4. **Mod**: put `farlands-g1-mod-<version>.jar` into `mods/`.
5. **Launch**: the banner `[FarLands-G1] <version> epoch build` means success.
6. **Create world**: set epoch/terrain on the FarLands tabs.

Full walkthrough: [docs/USAGE.en.md](docs/USAGE.en.md).

## Build from source (developers only)

```
gradlew clean build
```

Artifacts land in `patcher-cli/build/libs/` and `mod/build/libs/`. Only needed if you
want to change the code or compile it yourself.

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
