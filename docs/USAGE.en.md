# FarLands G1 Manual

> Directive style. Execute in order. Read [REVIEW](REVIEW.md) if terms are unclear.

## 0. Overview

| Item | Value |
|---|---|
| Goal | Real-coordinate exploration to 2^63 (eventually 1e306) |
| Coordinates | Real. No scaling. No fakery. |
| Boundary | Playable past 2^31 (terrain/collision/interaction intact) |
| Phenomena | Terrain quantizes from 2^53 (sightseeing zones) |

## 1. Install

### 1.1 Prerequisites

Must satisfy:

- Java 25
- Official 26.2 client jar (obtain from Mojang yourself)

### 1.2 Patch

Execute:

```powershell
java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" "-Dfarlands.epoch=true" `
  -jar patcher-cli-1.0-SNAPSHOT.jar `
  --in <official-26.2.jar> --out <fork.jar>
```

### 1.3 Deploy

Execute in order:

1. Back up the original jar (`<versions>\<version>.jar` -> rename and keep)
2. Replace it with the fork jar
3. Put `farlands-g1-mod-1.0-SNAPSHOT.jar` into `mods\`
4. Launch

> **Do NOT** add any JVM flags. Configuration is auto-created.

> **Warning**: replacing the jar without a backup is at your own risk.

## 2. Usage

### 2.1 Teleport

Execute:

```
/realtp <x> <y> <z>
```

For entities:

```
/realtp <targets> <x> <y> <z>
```

`<targets>` is an entity selector (`@p`, `@e`, player name). Command blocks work.

> **Warning**: coordinates are ALWAYS real coordinates. Do NOT use `~`.

Arbitrary precision is supported. All of these are valid:

```
/realtp 2147483647 100 0        -> real 2^31-1 (int boundary)
/realtp 10000000000 100 0       -> real 1e10
/realtp 1e1000 100 0            -> 1e1000 (arbitrary precision)
```

### 2.2 Crossing the window

Targets beyond the current epoch window (`epoch +/- 2^31`) trigger an
**archive relocation**:

1. The current epoch's chunks move to `world\<archive_dir>\`
2. The target epoch's archive is restored if present
3. The middle is never generated
4. The player lands at the target

Takes seconds (small saves) to tens of seconds (large saves).

### 2.3 Returning

Execute `/realtp <old origin>`. The archive restores; terrain is intact.

## 3. Configuration

`farlands.properties` in the world directory (auto-created). Re-enter the
world after editing.

```properties
# ---- epoch ----
epoch_x=0               # world origin (real coordinate, exact)
epoch_z=0
# ---- relocation ----
auto_relocate=true      # true=auto re-center near the edge; false=warn only
relocate_margin=100000  # trigger distance from the edge (blocks, >= 100000)
relocate_discard_over=2147483647  # shifts larger than this (chunks) use archive mode
# ---- performance ----
fluid_tick_limit=2000   # max fluid ticks per game tick (0 = unlimited)
# ---- storage ----
archive_dir=farlands_epochs  # per-epoch archive directory
# ---- debug ----
debug=false             # verbose logging
```

JVM flags override: `-Dfarlands.<key>=<value>`.

> **Warning**: changing `epoch_x/epoch_z` desyncs already-generated chunks.
> Prefer `/realtp` or a fresh world.

## 4. Distance phenomena (sightseeing)

| Real coordinate | Phenomenon |
|---|---|
| 2^53 (9.007e15) | Terrain stop point - adjacent samples merge |
| 2^63 (9.223e18) | Far lands - 2048-block homogeneous mosaic |
| 1.8e308 (double max) | Water-column world - horizontal collapse + normal vertical |
| 1e306 | Completely frozen |

> **Warning**: phenomenon zones are sightseeing areas. At 32 render distance the
> vertex count reaches hundreds of millions (8GB of vertex buffers, ~3fps on an
> iGPU). Short visits/screenshots only; `/realtp` back for playing.

## 5. Troubleshooting

**Symptom**: spawning far away on entering a world.

**Action**: that world's origin is persisted by design. Reset:

```properties
epoch_x=0
epoch_z=0
```

Or delete and recreate the world.

---

**Symptom**: fps collapse in a phenomenon zone.

**Action**: lower render distance to 10-12. Do NOT use 32 there.

---

**Symptom**: long black screen on relocation.

**Action**: normal. Save + archive + reload takes time. Wait.

---

**Symptom**: multiplayer issues.

**Action**: unsupported. Singleplayer (integrated server) only; multiplayer
needs protocol-level epoch sync (not implemented).

## 6. Limitations

- `> 1.8e308`: double overflow -> sampling returns a fixed value (phenomenon,
  not real terrain)
- Entity positions/rendering stay in the local domain (precision phenomena
  appear in terrain sampling only)
- Singleplayer only
