# FarLands G1 — Changelog

> Version 1.0.0 (authority: repo-root VERSION)

## 1.0.1

> **FATAL - upgrade required.** `1.0.0` contains a far-domain crash
> (`Requested chunk unavailable during world generation`): the `WgrPatch` guard
> never actually applied, so structure decoration beyond the local window
> crashes chunk generation. `1.0.1` fixes that, the `/realtp` one-ULP sampling
> bug, and the integer subsystems that repeated with the 2^32 local window.
> **If the startup banner shows `1.0.0`, update to `1.0.1`.**

### Terrain / far-domain fixes

- **Far-domain crash `Requested chunk unavailable during world generation`**:
  `WgrPatch`'s idempotency check keyed on the constant `134000000`, which
  vanilla's own `WorldGenRegion.getChunk` guard already contains -> the patch
  always saw itself as applied and **never injected anything**; in the
  local/epoch engine the region center is small, so the hard-fail branch was
  always taken and structure decoration (e.g.
  `MineshaftPieces.isInInvalidLocation` querying a biome outside the region)
  crashed chunk generation. The guard now keys on our own injected
  `FarProjection.isEpochActive` call and returns the center chunk while the
  epoch is active.
- **`/realtp` sampled one ULP off at extreme distances**: the epoch was floored
  to a 16-block grid; at extreme magnitudes that can cross a double bucket
  boundary (round-half-even then picks the lower double), so the target was
  sampled one ULP low and the far lands did not trigger.
  `floorTo16PreservingBucket` now aligns the epoch to the target's own double
  bucket when the floor would change the bucket.
- **Three patcher idempotency checks could be fooled by vanilla patterns**
  (`AabbClipPatch` on `move(DDD)` - present in vanilla `AABB.clip`;
  `BlockCollisionsPatch` on a method named `real`; `BoundingBoxPatch` on
  `Math.clamp`). Rewritten to key on their own injected markers.
- **Dev jar cache key included only the flags** (`wide/continuity/epoch/unlock`),
  so changing the patch set reused a stale patched jar. `PATCH_REVISION` added
  to `G1JarProcessor.Spec`.

### Integer subsystems real-coordinate-ized (B line)

Several generation subsystems still used the LOCAL int coordinate as if it were
the world coordinate, so far-domain terrain / biomes / structures / ores
repeated with the 2^32 local window while the main noise already used the real
coordinate. Fixed individually (epoch-gated; origin bit-identical): End island
density (`EndIslandMath`), carvers, features/decoration, biome climate
(`Climate$Sampler` via `RealContext`), surface material (`SurfaceSystem`),
density-chain `FindTopSurface`, carver aquifer (`WorldCarver`), The End biome
source, geodes, structure seeding (`Structure$GenerationContext` /
`StructurePlacement` / ocean monument / stronghold), ore veins, surface-rule
noise (2D/3D) and vertical-gradient random.

- **End island wide `getHeightValue` bit-exactness**: the pure-double wide
  version diverged from vanilla for `section >= 32768` (vanilla's `int` square
  overflows and the End world border is inside that range). `EndIslandMath` now
  uses the vanilla `int` path for `|section| <= 2^28` and the wide path only
  beyond.

### Cleanup

- Removed 8 unregistered / duplicate dead classes (incl. `DensityNoisePatch`,
  `WorldGenRegionEpochPatch` and four never-registered mixins).
- `FarProjection`: removed ~130 lines of unused conversion helpers and a
  corrupted comment.
- Stopped tracking build outputs / logs / rig run data (incl. a committed rcon
  and management secret) and extended `.gitignore`.
- Aligned docs (`EXPERIMENTS.md`, `FARLANDS-MECHANISM.md`, `CONFIG.md`) and the
  `exp-run.ps1` defaults (Delay=300 / Settle=400 / BgThreads=1) with the code.

### Research note - far-lands onset

- Machine test (`topY` scan, 2 seeds) places the main-noise destruction at the
  double bucket `k0 = 1808764368955220493860864` (the chronicle / INF bucket),
  within 1 ULP and seed-independent. The previously recorded value
  `1.80876436895000e24` is 19448 ULP below the onset and is superseded.
- Added a default-off diagnostic `-Dfarlands.diag.exactwrap=true` (exact
  `PerlinNoise.wrap`) to isolate the wrap mechanism's terrain impact.

### Other

- **UNLOCK lab module (opt-in, NOT in the default build)**: `OptionsUnlockPatch`
  raises the render/simulation distance caps (32/16 -> 96); gated by
  `-Dfarlands.unlock` AND `-Dfarlands.unlock.i_know_what_im_doing`; the client
  logs a Vulkan gate warning at startup. See docs/UNLOCK-DESIGN.md.
  **Runtime in-game test NOT performed (maintainer declined: high risk).**

## 1.0.0

Single version authority and configuration before world creation.

- **Single version authority**: repo-root `VERSION` (currently `1.0.0`) is the
  only authoritative version. It flows into `fabric.mod.json`, the jar names
  (`patcher-cli-1.0.0.jar`, `farlands-g1-mod-1.0.0.jar`) and the runtime banner
  `[FarLands-G1] <version> epoch build (realtp + relocate)`.
- **Global config template**: `config/farlands-g1.properties` (Fabric config
  dir) is auto-created at mod init, before any world exists, with documented
  defaults. New worlds inherit it - configuration is usable before world
  creation.
- **Create-world tabs**: the create-world screen gained three FarLands tabs (via
  a `CreateWorldScreen` mixin):
  - "FarLands": `epoch_x`, `epoch_z`, `auto_relocate`, `relocate_margin`,
    `debug`, plus the "Save as global default" button.
  - "Terrain": `worldgen_sample_mode`, `worldgen_sample_clamp`,
    `worldgen_far_threshold`, `pro_sample_offset_x`, `pro_sample_offset_z`,
    `pro_sample_scale`.
  - "Test": `testgen`, `testgen_stop`, `testgen_settle`, `testspawn`,
    `spawnset` (headless experiment controls).
- **Config audit**: removed the dead relocate\_discard\_over key (nothing read
  it); `debug` is now functional (`>=1` relocation summary, `>=2` fluid
  tick-limit hits, `>=3` per-sample terrain-transform log). The five
  test-harness keys (`testgen`, `testgen_stop`, `testgen_settle`, `testspawn`,
  `spawnset`) are now writable outside the JVM; `-Dfarlands.testgen` etc. still
  work and win.
- **World file written before generation**: on world creation,
  `<world>/farlands.properties` is written from those tabs before terrain
  generation. If the tabs were never touched, the world file is seeded from the
  global template when the world loads.
- **Removed `/farlands`**: the command (its mixin and its config methods) is
  deleted. Configuration is never edited from inside a running world - edit a
  file outside the world, then re-enter.
- **Pro options**: `pro_sample_offset_x/z` and `pro_sample_scale` are
  experimental and high-impact; defaults are strict no-ops.

## Historical

### v0.3 (J3 milestone)

A Minecraft 26.2 Far Lands toolkit: an ASM bytecode patcher + Fabric mod that
pushes the playable world to ±2^31 (2.1 billion blocks) at real coordinates.

#### What works (playable)

- ✅ Full playability within ±2^31: real terrain, working collisions, no
  seam chasm (the classic Far Lands lava trench is gone), no OOM, no freeze
- ✅ Both half-axes generate unique terrain (no more mirroring)
- ✅ Generation pipeline continuity fixes (aquifer grid, surface rules,
  noise sampling)
- ✅ Headless server test harness (`tools/server-test.ps1`)
- ⚠️ Fluid interaction (swimming/lava) is skipped beyond ±2 billion blocks
  (known limitation)

#### How to use

See `docs/ROADMAP.md` for build and deployment steps.

#### Status

- Milestones J1 / J2 / J3 verified and committed
- Next stage (E line, in progress): extend playable range toward 2^63
  (sliding epoch)

#### Notes

- You must supply your own Minecraft 26.2 client jar (this tool does not
  bundle the game).
