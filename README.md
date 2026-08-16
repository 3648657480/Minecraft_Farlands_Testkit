# FarLands G1

Minecraft 26.2 Far Lands patch toolset — removes the 30M coordinate limit so the
world keeps working up to the 64-bit integer boundary.

## What this project is (and is not)

This repository contains **only original code**: an ASM patcher library, a
standalone CLI, and a Fabric mod with runtime support (mixins + helpers). It
contains **no Minecraft source code, no decompiled output, and no game files**.

The patcher reads class bytes from a Minecraft client jar **you supply
yourself** (your own legitimately obtained copy, downloaded from Mojang) and
rewrites them in place on your machine.

## Requirements

- Java 25 (the same runtime Minecraft 26.2 uses)
- The official Minecraft 26.2 client jar, obtained by yourself

## Build

```
gradlew clean build
```

Produces:

- `patcher-cli/build/libs/patcher-cli-1.0-SNAPSHOT.jar` — standalone patcher
  (ASM bundled, no other dependencies)
- `mod/build/libs/farlands-g1-mod-1.0-SNAPSHOT.jar` — the runtime mod

## Patch your client jar

```
java -jar patcher-cli/build/libs/patcher-cli-1.0-SNAPSHOT.jar \
    --in <path-to-your-minecraft-client.jar> \
    --out <path-to-patched.jar>
```

The tool patches 13 classes and prints a report. It never downloads or embeds
Minecraft content; the input jar stays untouched.

## Install

1. Replace the client jar in your launcher profile with `<path-to-patched.jar>`
   (keep the profile's version id, rename the file accordingly).
2. Put `mod/build/libs/farlands-g1-mod-1.0-SNAPSHOT.jar` into the profile's
   `mods` folder (Fabric Loader profile).
3. Launch the game.

The patched client and the mod must be used together: the patched classes
call helpers shipped in the mod, and the mod's mixins expect the patched
game.

## Development

The same patch set is applied automatically at build time by the Loom
`MinecraftJarProcessor` registered in `mod/build.gradle`
(`com.farlands.g1.loom.G1JarProcessor`, defined in `buildSrc`). Edit a patch
in `patcher-core` and run `gradlew :mod:build` — the Minecraft jar used for
compilation is re-patched from the current sources.

## Legal notes

- Minecraft is a trademark and copyright of Mojang Studios / Microsoft. This
  project is an independent modification tool and is not affiliated with or
  endorsed by Mojang.
- The patcher transforms a jar on the user's machine; it does not download,
  embed, or redistribute any Minecraft content.
- The floating-origin and epoch-correction techniques follow the approach
  pioneered by [INF32768/UltimateScaler](https://github.com/INF32768/UltimateScaler)
  (MIT License) — used with gratitude, not copied code.

## License

MIT — see LICENSE.txt.
