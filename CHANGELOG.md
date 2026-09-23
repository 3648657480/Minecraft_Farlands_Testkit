# FarLands G1 — Changelog

> Version 1.0.0 (authority: repo-root VERSION)

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
