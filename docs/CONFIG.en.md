# FarLands G1 Configuration Guide

> Version 1.0.0 (authority: repo-root VERSION)
> Style: Strict Respect Style. The single configuration reference: global
> template, create-world tabs, world file, and per-key details.
> Every consequence carries a verification status. Unverified items are
> treated as red lines by default.

---

## 0. Basics

Configuration has three sources, in priority order (high -> low):

```
JVM override   -Dfarlands.<key>=<value>              at process start
  > world file   <world folder>\farlands.properties    at world load
    > global template  config\farlands-g1.properties   created at mod init
```

### 0.1 Global template

File: `config\farlands-g1.properties` (Fabric config dir).

Created: **at mod init**, before any world exists, auto-created with documented
defaults.

Purpose: the defaults for new worlds. You can edit it **before creating a
world**; worlds created afterwards inherit those values. A dedicated server has
no screen and uses the global template directly.

| State | Behaviour |
|---|---|
| File missing | Auto-created (with defaults) |
| File present | Seed values for new worlds |

### 0.2 Create-world tabs

The create-world screen has two FarLands tabs (injected via a
CreateWorldScreen mixin):

| Tab | Fields |
|---|---|
| **FarLands** | `epoch_x`, `epoch_z`, `auto_relocate`, `relocate_margin`, `debug`; plus the **"Save as global default"** button |
| **FarLands Terrain** | `worldgen_sample_mode`, `worldgen_sample_clamp`, `worldgen_far_threshold`, `pro_sample_offset_x`, `pro_sample_offset_z`, `pro_sample_scale` |

**Execute**: on world creation these values are written to
`<world>\farlands.properties` **before terrain generation**. Keys not shown on
the tabs (`relocate_discard_over`, `fluid_tick_limit`, `archive_dir`) take the
global template's values. If the tabs were never touched, the world file is
seeded from the global template at **world load**.

### 0.3 World file

File: `<world folder>\farlands.properties`. This is the world's configuration.

| State | Behaviour |
|---|---|
| File missing | Seeded from the global template at world load (epoch = origin) |
| File present, keys missing | Missing keys take defaults; missing epoch = origin |
| File present, malformed | **`POLICY VIOLATION` -> JVM aborted** (see R2) |

### 0.4 When changes apply

**Configuration is never edited from inside a running world.**

| Source | Applies at |
|---|---|
| Create-world tabs | world creation (written to the world file before generation) |
| World file | world load (back to the main menu and in again is enough; no process restart needed) |
| Global template | seed values for **new** worlds only; no effect on existing worlds |
| JVM override | process start (not persisted) |

### 0.5 Backup

**Execute**: copy `farlands.properties` to `farlands.properties.bak` before
editing.

Reason: a malformed file aborts the JVM; a backup lets you roll back instantly.

---

## 1. Per-key reference

### 1.1 `epoch_x` / `epoch_z`

| Item | Value |
|---|---|
| Meaning | World origin (real coordinate). Engine local = real - origin |
| Type | Any integer (arbitrary precision; `1e306` valid) |
| Default | `0` / `0` |
| Verification | Manual edit -> chunk desync: **simulated** (derived from the cross-world leak incident) |

**How to use**:

- Normal play: do not touch. Use `/realtp`; the system maintains the origin.
- Long-term stay at a new location: `/realtp <target>` sets the origin to the
  target automatically (archive relocation).
- Reset to origin: set `epoch_x=0`, `epoch_z=0`, re-enter the world.

**Wrong-value consequence**: manually editing an existing world's origin ->
**every generated chunk desyncs** (the engine interprets old data against the
new origin).

**Why unrestricted**: the system cannot distinguish a correct migration
(shift the save first) from a wrong edit. This is R1.

**Interactions**: `/realtp`, the `farlands_epochs\` archive.

---

### 1.2 `auto_relocate`

| Item | Value |
|---|---|
| Meaning | Auto re-center when walking near the window edge |
| Type | `true` / `false` |
| Default | `true` |
| Verification | Auto relocation flow: **tested** (E5 milestone) |

**How to use**:

- `true` (default): walk on; near `epoch +/- (2^31 - relocate_margin)` the
  world re-centers automatically and you continue seamlessly.
- `false`: only a warning ("near the window edge, use /realtp"). For fully
  manual control.

**Wrong-value consequence**: non-boolean (`yes`, `1`) -> **`POLICY VIOLATION`
-> JVM aborted**.

**Why strict**: a silent fallback would turn "I wrote true" into "actually
false"; intent gets hidden. See R2.

**Interactions**: `relocate_margin`.

---

### 1.3 `relocate_margin`

| Item | Value |
|---|---|
| Meaning | Distance from the window edge that triggers (or warns) relocation |
| Type | integer >= 0 (blocks) |
| Default | `100000` |
| Verification | Trigger behaviour: **tested** |

**How to use**:

- Default 100000: ample margin (feature placement needs ~2000 blocks; 100000
  is far beyond that).
- Larger (`1000000000`): the edge is even less visible ("edge never in
  sight"); the cost is more frequent relocations.
- Smaller (`5000`): relocation happens later; risk of approaching the int
  overflow zone (**below 10000 not recommended**).

**Wrong-value consequence**: negative -> **`POLICY VIOLATION` -> JVM aborted**.
Too large -> frequent relocations while walking (usable but annoying).

**Why 0 is allowed**: 0 means "only at the very edge" - valid semantics but
risky; negative is an error.

**Interactions**: `auto_relocate`.

---

### 1.4 `relocate_discard_over`

| Item | Value |
|---|---|
| Meaning | Shifts larger than this (chunks) switch to archive mode |
| Type | integer >= 1 (chunks) |
| Default | `2147483647` (2^31-1) |
| Verification | Archive mode: **tested** (E5) |

**How to use**:

- Default: shifts within 2^31 chunks use in-place shifting (old content
  follows); larger shifts archive.
- Note: `/realtp` always archives (this key only affects the shift path).

**Wrong-value consequence**: < 1 -> **`POLICY VIOLATION` -> JVM aborted**.

**Why**: shift amounts must be positive; 0 or negative is meaningless.

---

### 1.5 `fluid_tick_limit`

| Item | Value |
|---|---|
| Meaning | Max fluid ticks per game tick |
| Type | integer >= 0 (0 = unlimited) |
| Default | `2000` |
| Verification | Unlimited -> CPU saturation: **tested** (watchdog stack) |

**How to use**:

- Default 2000: imperceptible in normal terrain; prevents CPU saturation in
  anomalous terrain (water-column worlds).
- Larger (`10000`): faster fluid flow; risk of CPU pressure in anomalous
  terrain (see R4).
- `0`: disable the limit. **Normal terrain only**; anomalous terrain + 0 = red
  line R4.

**Wrong-value consequence**: negative -> **`POLICY VIOLATION` -> JVM aborted**;
`0` + anomalous terrain -> the server thread stays saturated, AppHang (tested).

**Why**: every fluid block runs a recursive slope search (4-8 levels x 3
directions). A water-column world has millions of fluid blocks; unlimited =
CPU saturation.

**Interactions**: phenomenon zones (beyond 2^53).

---

### 1.6 `archive_dir`

| Item | Value |
|---|---|
| Meaning | Epoch archive directory name (relative to the world) |
| Type | plain directory name (no path separators) |
| Default | `farlands_epochs` |
| Verification | Archive/restore: **tested** |

**How to use**:

- Default is fine. Layout: `<archive_dir>\e_<first 12 digits>L<digit
  count>_<same for Z>\` (one subdirectory per visited epoch).
- Relocating it (e.g. to another disk) is **not supported** (must be relative
  to the world). Use a symlink instead.

**Wrong-value consequence**: empty / contains `/`, `\`, `..` -> **`POLICY
VIOLATION` -> JVM aborted**.

**Why**: path traversal writes outside the world and can overwrite system files.

**Interactions**: `/realtp` archive relocation.

---

### 1.7 `worldgen_sample_mode`

| Item | Value |
|---|---|
| Meaning | Transform for far sample coordinates (beyond `worldgen_far_threshold`) |
| Type | `raw` / `clamp` / `quantize` |
| Default | `raw` |
| Verification | Three modes at 1e306: **tested**; terrain discontinuity: **unverified (treated as a red line)** |

**How to use**:

| Value | Effect | For |
|---|---|---|
| `raw` | Native double sampling; phenomena emerge naturally | Normal play, sightseeing |
| `clamp` | Clamp sample coordinates to +/-`worldgen_sample_clamp`; finite but repeated terrain | Avoiding Infinity (>1.8e308) with repeated terrain |
| `quantize` | Snap sample coordinates to the 2^53 grid; very flat/stable | Flat-world experiments |

**Wrong-value consequence**: invalid value (typo) -> **`POLICY VIOLATION` ->
JVM aborted**. `clamp`/`quantize` change terrain -> discontinuity (R5).

**Why strict**: a typo (`Raw`, `raw `) must not silently mean raw - you would
think your policy is active when it is not.

**Interactions**: `worldgen_sample_clamp`, `worldgen_far_threshold`.

---

### 1.8 `worldgen_sample_clamp`

| Item | Value |
|---|---|
| Meaning | Clamp bound (blocks) for clamp mode |
| Type | positive finite number |
| Default | `1e300` |
| Verification | Generation: **tested** |

**How to use**:

- `1e300`: within double's range (1.8e308), avoids Infinity.
- Smaller (`1e10`): stronger terrain repetition (denser sample points).
- Larger (`1e307`): close to double's limit; collapse may still occur.

**Wrong-value consequence**: non-positive or NaN/Infinity -> **`POLICY
VIOLATION` -> JVM aborted**.

**Why**: 0 or NaN makes sampling meaningless (all coordinates map to one point).

**Interactions**: used only with `worldgen_sample_mode=clamp`.

---

### 1.9 `worldgen_far_threshold`

| Item | Value |
|---|---|
| Meaning | Distance (blocks from the origin) beyond which the policy applies |
| Type | integer >= 0 |
| Default | `9007199254740992` (2^53) |
| Verification | Everywhere (0): **unverified (treated as a red line)** |

**How to use**:

- Default 2^53: the policy applies only beyond double's precision limit;
  terrain below 2^53 is fully native.
- Larger: the policy applies later (`1e18`).
- `0`: apply everywhere - **experiments only**; rewrites normal-area terrain
  (R5).

**Wrong-value consequence**: negative -> **`POLICY VIOLATION` -> JVM aborted**.
`0` -> terrain rewritten everywhere (unverified; treated as a red line).

**Why**: the threshold must be non-negative; 0 has clear semantics but is
dangerous.

**Interactions**: `worldgen_sample_mode`.

---

### 1.10 `debug`

| Item | Value |
|---|---|
| Meaning | Log level |
| Type | `0` / `1` / `2` / `3` |
| Default | `0` |
| Verification | Out of range -> halt: **tested**; level-3 log volume: **simulated (not measured)** |

**How to use**:

| Value | Output | For |
|---|---|---|
| `0` | Off (key events only) | Normal play |
| `1` | Basic events (epoch set, relocation) | Routine diagnostics |
| `2` | Detailed (config reads, decision paths) | Deep diagnostics |
| `3` | **Everything** (per-chunk / per-tick traces) | **Short diagnostics (R3)** |

**Wrong-value consequence**: out of 0-3 -> **`POLICY VIOLATION` -> JVM
aborted**. Level 3 long-term -> enormous logs (disk/performance pressure, R3).

**Why four levels**: 0/1/2/3 have fixed semantics; out-of-range is an error
(not "auto-clamp to 3").

---

## 2. Presets

Fill in the create-world tabs, or write only the differing keys into the world
file.

### P1 Standard (default)

For: normal play.

```properties
epoch_x=0
epoch_z=0
auto_relocate=true
relocate_margin=100000
relocate_discard_over=2147483647
fluid_tick_limit=2000
archive_dir=farlands_epochs
worldgen_sample_mode=raw
worldgen_sample_clamp=1e300
worldgen_far_threshold=9007199254740992
debug=0
```

### P2 Safe / conservative

For: irreplaceable worlds; stable exploration only.

```properties
auto_relocate=false
relocate_margin=1000000
fluid_tick_limit=1000
debug=0
```

(rest default. `auto_relocate=false` = walking never interrupts; relocation
happens only on `/realtp`.)

### P3 Performance first

For: weak machines (iGPU / low RAM).

```properties
relocate_margin=1000000000
fluid_tick_limit=500
debug=0
```

(Also: lower the in-game render distance to 8-10. Phenomenon zones remain
heavy - see R6.)

### P4 Terrain experiments

For: experimenting with `worldgen_*` **in a fresh world** (R5: prohibited in
irreplaceable worlds).

```properties
worldgen_sample_mode=quantize
worldgen_far_threshold=0
debug=1
```

(rest default. For clamp: `worldgen_sample_mode=clamp` +
`worldgen_sample_clamp=1e10`.)

### P5 Sightseeing

For: visiting phenomenon zones / screenshots.

```properties
fluid_tick_limit=2000
debug=0
```

(Also: **lower the render distance to 4-6 first**, then
`/realtp 9223372036854775807 100 0`. Return with `/realtp 0 100 0`.)

### P6 Diagnostics

For: troubleshooting (short sessions).

```properties
debug=2
```

(Set back to 0 after reproducing. **Do not** run `debug=3` long-term - R3.)

### P7 Vanilla-compatible

For: boundary crossing only, no automation.

```properties
auto_relocate=false
worldgen_sample_mode=raw
debug=0
```

(Also: use `/realtp` manually only.)

---

## 3. JVM overrides

Usage:

```powershell
java -Dfarlands.debug=1 -Dfarlands.relocate_margin=500000 -jar <launcher>
```

Rules:

- Key form: `-Dfarlands.<config key>=<value>` (one-to-one with file keys).
- Priority: JVM overrides the world file, which overrides the global template
  (JVM flags are not persisted).
- Use for: temporary tests without editing the file.
- **Warning**: JVM flags go through the same policy checks; invalid values
  halt the same way.

---

## 4. Error handling

| Error | System behaviour |
|---|---|
| Global template missing | Auto-created at mod init |
| World file missing | Seeded from the global template at world load (epoch = origin) |
| Missing keys | Defaults |
| Malformed (non-number/non-boolean) | `POLICY VIOLATION` -> **JVM aborted** |
| Out-of-range (debug=5, negatives) | `POLICY VIOLATION` -> **JVM aborted** |
| Semantic error (`epoch` hand-edit) | undetectable -> chunk desync (R1) |

**Execute**: after `POLICY VIOLATION`, read the specific message in the log
(it names the offending key and value), fix, restart.

---

## 5. Scenario Q&A

**Q: I want to return to the origin.**
A: `/realtp 0 100 0` (archive restore). Or set `epoch_x=0`, `epoch_z=0` and
re-enter (**only if you accept losing the old location's content**).

**Q: I want walking to never interrupt.**
A: `auto_relocate=false` (P2).

**Q: I want to see 1e306.**
A: `/realtp 1e306 100 0`. **Lower the render distance to 4-6 first** (R6).

**Q: I want to experiment with clamp terrain.**
A: **Fresh world** -> `worldgen_sample_mode=clamp` + `worldgen_sample_clamp=1e10`
+ `worldgen_far_threshold=0` (P4).

**Q: My machine stutters.**
A: P3 + lower render distance. Phenomenon zones stay heavy (R6).

**Q: I changed `epoch_x` without shifting the save - how do I recover?**
A: Set `epoch_x` back to the old value (chunks recover immediately). **This is
why the system does not block it** - rollback is possible (provided you
remember the old value; hence the backup in 0.3 is an instruction).

**Q: Does `debug=3` corrupt worlds?**
A: No, but it produces enormous logs (disk pressure). **Not measured - treated
as a red line** (R3).

---

## 6. Pro options (experimental, not for normal players)

> **WARNING**: this group rewrites the **noise input** of terrain generation -
> terrain is morphed **everywhere** (not just far away). All defaults are strict
> no-ops. Fresh/test worlds only. Consequences are yours (see USAGE red lines).

| Key | Meaning | Default | Range | Consequence |
|---|---|---|---|---|
| `pro_sample_offset_x` | offset added to the noise input X (blocks) | `0` | finite | terrain shifts along X; non-zero = discontinuity with existing terrain |
| `pro_sample_offset_z` | offset added to the noise input Z (blocks) | `0` | finite | same, Z axis |
| `pro_sample_scale` | multiplier on the noise input X/Z | `1` | > 0, finite | >1 zooms features out, <1 in; breaks continuity |

**Why the defaults must be no-ops**: they affect **all** terrain (including the
spawn area). Any non-default value makes newly generated terrain discontinuous
with old terrain - **do not** use them in normal saves.

**Wrong-value consequence**: non-finite / scale <= 0 -> `POLICY VIOLATION` ->
JVM aborted.

---

## 7. Create-world screen + global template

### 7.1 Tabs

The create-world screen has two FarLands tabs; the choices are written to the
world file before generation.

- **FarLands**: `epoch_x`, `epoch_z`, `auto_relocate`, `relocate_margin`, `debug`
- **FarLands Terrain**: `worldgen_sample_mode`, `worldgen_sample_clamp`, `worldgen_far_threshold`, `pro_sample_offset_x`, `pro_sample_offset_z`, `pro_sample_scale`

The epoch can be chosen before creation - this is the solution to the "config is
created on world entry but cannot be edited afterwards" contradiction.
**"Save as global default"** writes the current tab values back to the global
template.

**Screen behavior**:

- A red warning asks you to read `docs/CONFIG.md` first and states that a wrong value aborts the game.
- **Inputs stay disabled until you tick "I have read the manual and filled per it"**.
- Language is automatic: Chinese for a Chinese game, English otherwise.

### 7.2 Global template

- Path: `config\farlands-g1.properties` (Fabric config dir)
- Auto-created at mod init (before any world), with documented defaults
- New worlds (with or without the screen) inherit it
- A dedicated server has no screen and uses the global template directly

### 7.3 Changing an existing world

**Execute**: leave the world -> edit `<world>\farlands.properties` (or the
global template) -> re-enter the world.

**Configuration is never edited from inside a running world.** Prefer `/realtp`
for the epoch; a manual edit desyncs generated chunks (see R1).
