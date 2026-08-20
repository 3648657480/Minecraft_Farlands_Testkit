# FarLands G1 — v0.3 (J3 milestone)

A Minecraft 26.2 Far Lands toolkit: an ASM bytecode patcher + Fabric mod that
pushes the playable world to ±2^31 (2.1 billion blocks) at real coordinates.

## What works (playable)

- ✅ Full playability within ±2^31: real terrain, working collisions, no
  seam chasm (the classic Far Lands lava trench is gone), no OOM, no freeze
- ✅ Both half-axes generate unique terrain (no more mirroring)
- ✅ Generation pipeline continuity fixes (aquifer grid, surface rules,
  noise sampling)
- ✅ Headless server test harness (`tools/server-test.ps1`)
- ⚠️ Fluid interaction (swimming/lava) is skipped beyond ±2 billion blocks
  (known limitation)

## How to use

See `docs/ROADMAP.md` for build and deployment steps.

## Status

- Milestones J1 / J2 / J3 verified and committed
- Next stage (E line, in progress): extend playable range toward 2^63
  (sliding epoch)

## Notes

- You must supply your own Minecraft 26.2 client jar (this tool does not
  bundle the game).
