# FarLands G1 Manual

> Style: Strict Respect Style.
> Directive, but explains why; zero-tolerance, but consequences are verified;
> gives freedom, but does not indulge ignorance.
> Every consequence below carries a verification status. Unverified items are
> treated as red lines by default.

---

## 1. Instructions

### 1.1 Install

Execute:

1. Back up the original jar (copy it to `<version>.jar.bak`)
2. Patch:

```powershell
java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" "-Dfarlands.epoch=true" `
  -jar patcher-cli-1.0-SNAPSHOT.jar `
  --in <official-26.2.jar> --out <fork.jar>
```

3. Replace the same-named jar in the versions folder with the fork jar
4. Put `farlands-g1-mod-1.0-SNAPSHOT.jar` into `mods\`
5. Launch

### 1.2 Teleport

Execute:

```
/realtp <x> <y> <z>
/realtp <targets> <x> <y> <z>
```

`<targets>` is an entity selector (`@p`, `@e`, player name). Command blocks work.
Coordinates are always real coordinates. Arbitrary precision (`1e1000` is valid).

### 1.3 Crossing the window

Execute `/realtp` with an out-of-window target. The system will:

1. Archive the current epoch's chunks to `world\<archive_dir>\`
2. Restore the target epoch's archive if present
3. Reload and land at the target

### 1.4 Returning

Execute `/realtp <old origin>`. The archive restores automatically.

---

## 2. Parameters

Config file: `world\farlands.properties` (auto-created). Re-enter the world
after editing.

| Parameter | Meaning | Range | Wrong-value consequence | Why this limit |
|---|---|---|---|---|
| `epoch_x` / `epoch_z` | World origin (real coordinate) | any integer | **All generated chunks desync** (see red line R1) | Unrestricted - but the consequence is irreversible and undetectable by the system |
| `auto_relocate` | Auto re-center near the window edge | `true` / `false` | Non-boolean -> **JVM aborted** | Semantics must be explicit; silent fallback hides intent |
| `relocate_margin` | Trigger distance (blocks) | >= 0 | Negative -> **JVM aborted**; too large -> late relocation (still usable) | Negative is meaningless; too small relocates constantly |
| `relocate_discard_over` | Shifts larger than this (chunks) use archive mode | >= 1 | < 1 -> **JVM aborted** | Shift amounts must be positive |
| `fluid_tick_limit` | Fluid ticks per game tick | >= 0 (0 = unlimited) | Negative -> **JVM aborted**; too large -> CPU burn in anomalous terrain (see R4) | 0 is valid semantics (off); negative is an error |
| `archive_dir` | Epoch archive directory name | plain directory name | Empty / contains `/`, `\`, `..` -> **JVM aborted** | Path traversal writes outside the world |
| `worldgen_sample_mode` | Far sampling policy | `raw` / `clamp` / `quantize` | Invalid -> **JVM aborted**; `clamp`/`quantize` change terrain (see R5) | The mode must be explicit; a typo must not silently mean raw |
| `worldgen_sample_clamp` | Clamp bound | positive finite | Non-positive/NaN -> **JVM aborted**; too small -> heavy terrain repetition | 0 or NaN makes sampling meaningless |
| `worldgen_far_threshold` | Policy applies beyond this (blocks) | >= 0 (0 = everywhere) | Negative -> **JVM aborted**; 0 -> terrain rewritten everywhere (see R5) | Negative is meaningless |
| `debug` | Log level | `0`-`3` | Out of range -> **JVM aborted**; `3` -> enormous output (see R3) | Four fixed levels; out-of-range is an error |

JVM flags override: `-Dfarlands.<key>=<value>`. JVM flags have equal weight and
are not persisted.

---

## 3. Red lines

### R1: Editing `epoch_x` / `epoch_z`

Prohibited: manually editing the epoch of an existing world.

Reason: all generated chunks are stored against the old origin. After the
edit the engine interprets them against the new origin - **every chunk
desyncs**.

Verification: tested (the 2026-09 cross-world leak reproduced the same class
of desync).

Disposition: the system does not block it (it cannot tell a correct migration
from a wrong edit). Prefer `/realtp` or a fresh world.

### R2: Malformed configuration

Prohibited: launching with invalid values.

Reason: a wrong epoch or margin destroys worlds; a silent fallback hides the
mistake.

Verification: tested (`debug=5` -> `POLICY VIOLATION` + JVM halt).

Disposition: `POLICY VIOLATION` -> `Runtime.halt(1)`. **The system will not
let you run with a broken config.**

### R3: `debug=3` for normal play

Prohibited: `debug=3` outside short diagnostic sessions.

Reason: per-chunk / per-tick trace output; disk and performance pressure.

Verification: simulated (log-volume reasoning). **Not measured - treated as a
red line.**

Disposition: none (user responsibility). Short diagnostics only.

### R4: Disabling the fluid limit in anomalous terrain

Prohibited: `fluid_tick_limit=0` in a water-column world.

Reason: every fluid block runs a recursive slope search; anomalous terrain
explodes the fluid tick count and burns the server thread in RUNNABLE
(watchdog-confirmed stack).

Verification: tested (2026-09 stress test: `Can't keep up! 95 ticks behind`,
ending in AppHang).

Disposition: none (user responsibility). Keep the default 2000.

### R5: `worldgen` experiments

Prohibited: experimenting with `worldgen_*` in a world you cannot replace.

Reason: changing the sampling policy makes newly generated terrain
discontinuous with existing terrain; `clamp`/`quantize` produce repeated/flat
terrain; `worldgen_far_threshold=0` rewrites terrain everywhere.

Verification: tested (1e306: all three modes generate without crashing);
**terrain discontinuity is unverified - treated as a red line.**

Disposition: none (user responsibility). Experiment in a fresh world.

### R6: Long sessions in phenomenon zones

Prohibited: long play beyond 2^53.

Reason: phenomenon geometry is extremely heavy. At 32 render distance the
vertex count reaches hundreds of millions (8GB of vertex buffers, ~3fps on an
iGPU); once virtual memory runs out the main thread starves (input dies while
the render thread keeps drawing).

Verification: tested (2026-09 stress test: AppHangB1, killed via Task Manager).

Disposition: none (user responsibility). Short visits/screenshots are fine;
`/realtp` back for playing.

---

## 4. Rationale

**Why is the epoch persisted instead of reset on every join?**
Because the world is stored against the epoch. Resetting would desync all
far-side saves. Persistence is protection, not restriction.

**Why does a bad config abort the JVM instead of falling back to defaults?**
Because a wrong `epoch_x` or `relocate_margin` destroys worlds. Defaults hide
the mistake until you discover the damage hours later. Aborting is protection.

**Why keep so many parameters open at all?**
Because you are a hardcore user. Open parameters + documented consequences +
verified red lines = informed freedom. The system does not decide for you, and
does not indulge ignorance.

**Why not forbid entering phenomenon zones?**
Because the phenomena are the project's goal. The system provides warnings and
guards (fluid limit), not prohibitions.

---

## 5. Verification

| Consequence | Status | Test conditions |
|---|---|---|
| Terrain continuous across 2^31, AABB works | tested | 2026-09, client at 2147481648 |
| Cross-world epoch leak (new world inherits old epoch) | tested | 2026-09, reproduced on world creation + verified after fix |
| `debug=5` -> JVM abort | tested | rig, `POLICY VIOLATION` + halt confirmed |
| `worldgen` three modes at 1e306 | tested | rig, seed 12345, raw/clamp/quantize all gen OK |
| 32 render distance in a phenomenon zone -> 3fps + paging | tested | 2026-09 stress test (AppHangB1) |
| Unlimited fluid ticks -> CPU burn | tested | watchdog stack (`getSlopeDistance` recursion chain) |
| `debug=3` log volume | simulated | log-volume reasoning (not measured) |
| `worldgen` terrain discontinuity | unverified | no test conditions - treated as a red line |
| Manual `epoch_x` edit -> chunk desync | simulated | derived from the cross-world leak incident (same mechanism) |

---

## 6. Freedom

You may adjust:

- `auto_relocate`, `relocate_margin`, `relocate_discard_over` (relocation behavior)
- `fluid_tick_limit` (performance trade-off)
- `worldgen_*` (terrain experiments - in a fresh world)
- `debug` (0-2 safe; 3 see R3)

Before adjusting you need to know:

- each parameter's wrong-value consequence (see Parameters)
- which are red lines (see Red lines)
- why (see Rationale)

If you break it, it is on you.
The system will not let you run with a broken config.

---

## 7. Known limitations

- `> 1.8e308`: double overflow -> sampling returns a fixed value (phenomenon,
  not real terrain)
- Entity positions/rendering stay in the local domain (precision phenomena
  appear in terrain sampling only)
- Singleplayer only (multiplayer needs protocol-level epoch sync, not implemented)

---

## Appendix: phenomenon coordinates

| Real coordinate | Phenomenon | Verification |
|---|---|---|
| 2^53 (9.007e15) | Terrain stop point (adjacent samples merge) | tested |
| 2^63 (9.223e18) | Far lands (2048-block homogeneous mosaic) | tested |
| 1.8e308 | Water-column world (horizontal collapse + normal vertical) | tested |
