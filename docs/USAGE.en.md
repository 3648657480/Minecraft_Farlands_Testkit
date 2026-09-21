# FarLands G1 Usage Guide

> Real-coordinate exploration up to 2^63 (eventually 1e306) - no scaling, no fake
> coordinates, real terrain.

## What this is

Minecraft 26.2's coordinate limit is +/-2^31 (~2.1 billion blocks); past it the
world wraps/crashes. This project uses a **patcher + mod** to keep the world
working at real coordinates:

- **Playable past the 2^31 boundary**: continuous terrain, working collision,
  breaking and placing
- **Arbitrary-distance travel**: real-coordinate teleport (supports 1e1000+)
- **Distance phenomena**: terrain naturally collapses at extreme coordinates
  following double precision (sightseeing zones)

## Install

**Requires**: Java 25 + your own official 26.2 client jar

1. **Patch** (produce the fork jar):
```powershell
java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" "-Dfarlands.epoch=true" `
  -jar patcher-cli-1.0-SNAPSHOT.jar `
  --in <official-26.2.jar> --out <26.2-Fabric_fork.jar>
```
2. **Replace the version jar** with the fork jar (back up the original)
3. **Install the mod**: put `farlands-g1-mod-1.0-SNAPSHOT.jar` into `mods/`
4. **Launch**: no JVM flags needed (config auto-created)

## Quick start

1. **Create a world** - you spawn near the origin (`farlands.properties` is
   auto-created)
2. **Teleport to the boundary**:
```
/realtp 2147483647 100 0      -> real 2^31-1 (the int limit)
```
3. **Go deeper** (out-of-window targets relocate automatically, a few seconds):
```
/realtp 10000000000 100 0      -> real 1e10
/realtp 9007199254740992 100 0 -> 2^53 (terrain stop point)
/realtp 9223372036854775807 100 0 -> 2^63 (far lands)
```
4. **Return**: `/realtp 0 100 0` (the archive is restored, terrain intact)

## Commands

```
/realtp <x> <y> <z>                yourself (real coordinates)
/realtp <targets> <x> <y> <z>      entities (@p / @e / name - command blocks work)
```

- Coordinates are arbitrary precision: `/realtp 1e1000 100 0` works
- Vanilla `/tp` keeps its local semantics (used internally)

## Configuration

`farlands.properties` in the world directory (auto-created, editable):

```properties
epoch_x=0               # world origin (real coordinate, exact)
epoch_z=0
auto_relocate=true      # true=auto re-center near the window edge; false=warn only
relocate_margin=100000  # trigger distance (blocks)
```

JVM flags can override (`-Dfarlands.spawnset=x,y,z` etc.) but are not needed.

## Distance phenomena (sightseeing)

| Real coordinate | Phenomenon |
|---|---|
| 2^53 (9.007e15) | **Terrain stop point** - adjacent samples merge, terrain quantizes |
| 2^63 (9.223e18) | **Far lands** - 2048-block homogeneous mosaic (crystal columns) |
| 1.8e308 (double max) | **Water-column world** - horizontal collapse + normal vertical + ocean fill |
| 1e306 | Completely frozen |

**Note**: phenomenon zones are **sightseeing areas** (geometry is extremely
heavy: 32 render distance can hit 8GB of vertex buffers and ~3fps on an iGPU).
Short visits/screenshots are fine; `/realtp` back to normal areas for playing.

## FAQ

**Q: I spawn far away on entering a world?**
A: That world was `/realtp`-ed before - the origin is persisted by design
(otherwise far-side saves would desync). Reset: edit `farlands.properties`
(`epoch_x=0`) or delete and recreate the world.

**Q: Only 3fps in the phenomenon zone?**
A: Phenomenon geometry is complex (water columns / block mosaics); at 32 render
distance that is hundreds of millions of vertices - a physics limit. Lower the
render distance to 10-12.

**Q: Where are my saves? How do I get back to the start?**
A: Every visited origin is archived under `world/farlands_epochs/`.
`/realtp <old origin>` restores its archive automatically (terrain intact).

**Q: The teleport takes a long black screen?**
A: Relocation saves + archives + reloads - seconds for small saves, tens of
seconds for large ones.

**Q: Multiplayer?**
A: Singleplayer only for now (integrated server). Multiplayer needs protocol
epoch sync (not implemented).

## Known limitations

- **>1.8e308**: double overflow -> sampling returns a fixed value (a phenomenon,
  not real terrain)
- **Entity positions/rendering** stay in the local domain (precision phenomena
  appear in terrain sampling only)
- **Phenomenon zones** are sightseeing areas, not for long play
- **Singleplayer only**
