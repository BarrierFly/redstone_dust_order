# AGENTS.md

Notes for anyone — human or model — working on this repository. Read this
before changing the model or the palettes.

## What this project is

A Fabric client-side mod that colours `minecraft:redstone_wire` by the order
vanilla updates its 7 update cores in. The whole mechanism, the mathematics and
the constraints on the design are in `README.md`; this file is about how the
*code* is arranged and what is load-bearing.

## Ground rules

1. **The model is verified, do not re-derive it.** `src/main/java/.../order/`
   is pure Java with no Minecraft imports. Its correctness is pinned by
   `RedstoneHashTest`, `OrderTableTest` and `PaletteTest`. If you change anything
   there, the tests should fail, and that is the point.
2. **Nothing in `order/` may import Minecraft.** That is what makes the model and
   all three palettes testable without a game on the classpath, and it keeps the
   hot path free of anything version specific.
3. **The mod must not touch `assets/minecraft/**`.** It replaces a block tint
   through vanilla's registry and nothing else. That is what lets it coexist
   with every third-party resource pack. Adding a model, texture or blockstate
   override would break that guarantee.
4. **Never rebuild chunk meshes from inside a render frame.** Go through
   `ChunkRebuild.request()`; the single rebuild happens on the next client tick.

## Repository layout

```
src/main/java/                 version independent; compiled by every branch
  order/                        the model, the table and the three palettes
  client/                       config, render-path state, entry point, self check
  command/                      /rsusoc

src/main/java-legacy/          1.19.4 - 1.21.x   tint, mesh rebuild, world warning
src/main/java-legacy-keys/     1.19.4 - 1.21.1   key binding
src/main/java-mid-keys/        1.21.10, 1.21.11   key binding
src/main/java-modern/          26.x              tint, mesh rebuild, world warning
src/main/java-modern-keys/     26.x              key binding

src/test/java/                 one shared test suite for every branch
src/test/java/.../tools/       TablePngDump, an offline palette-evaluation tool

versions/<mc>/gradle.properties per branch: minecraft_version, mcVersion, api_era
```

### Why source directories and not `//#if MC >= ...`

The `fabric-mod-template` skeleton uses the ReplayMod preprocessor with
`//#if`/`//$$` comments. That works by reverse-applying per-version diffs stored
under `versions/<mc>/src`. This repository has no such diffs, so the preprocessor
passes the sources through untouched and the conditional comments would compile
the wrong branch silently — which is exactly what happened during development.

`api_era` in each `versions/<mc>/gradle.properties` selects which source
directories go on the source path (see the table in `common.gradle`). Two
properties of this approach that matter:

- it is impossible to put two copies of one class on one source path, so an
  overlapping directory is a build failure rather than a mystery;
- an IDE can answer "which file does 1.21.1 actually compile".

Adding a supported version means: add `versions/<new>/gradle.properties`, list it
in `settings.json`, and if it needs different code, add the directory to
`eraSourceDirs`. Nothing else.

### Three things that changed in Minecraft, and where they live

| change | versions | file |
|---|---|---|
| `BlockColor` → `BlockTintSource` | 26.x | `java-legacy` vs `java-modern` `OrderTint` |
| `LevelRenderer#allChanged()` → `invalidateCompiledGeometry(...)` | 26.x | `java-legacy` vs `java-modern` `ChunkRebuild` |
| `KeyMapping` category `String` → `Category` record | 1.21.10+ | `java-legacy-keys` vs `java-mid-keys` |
| `KeyBindingHelper` → `KeyMappingHelper`, `Type.KEYSYM` → `KEYBOARD` | 26.x | `java-mid-keys` vs `java-modern-keys` |
| `Gui#getChat()` → `Gui#chatListener()` | 26.x | `java-modern` `WorldWarnings` |

Everything else is identical across all six versions and was verified by
inspecting the mapped jars rather than by guessing.

## The tint, specifically

Registered through vanilla's `BlockColors#register`, **not** Fabric's
`ColorProviderRegistry.BLOCK`. That API wraps rather than replaces whenever
vanilla already has a provider for the block, so it would not override the tint.
Vanilla's registry bottoms out in `IdMapper#addMapping` → `List#set`, so
registering after `createDefault()` overwrites cleanly. Verified by bytecode on
1.19.4, 1.21.1 and 1.21.11.

Two consequences that are easy to get wrong:

- On 26.x, `BlockTintSource#colorAsTerrainParticle` defaults to
  `colorInWorld`. Left alone, break particles and terrain particles would carry
  order colours. It is overridden on purpose.
- On 26.x, `relevantProperties` must include `POWER`, or the tint cache returns a
  stale colour whenever `alpha < 1`.

## Building

```
gradlew buildAndGather                 every branch, jars collected into build/libs
gradlew :1.21.11:test                  just the development branch's tests
gradlew :26.3:build                    one branch
```

Notes:

- **26.3 needs a JDK 25 toolchain**; the other branches compile on whatever runs
  Gradle. `common.gradle` requests the toolchain only for the unobfuscated
  branch, and `gradle.properties` disables toolchain auto-download.
- **Expect a long first configuration.** Loom remaps Minecraft for every branch
  during configuration, and configuration alone has been observed at over twenty
  minutes after a cache wipe. If a build looks stuck, check the log before
  assuming it is hung.
- **Avoid `clean`.** It invalidates every Loom cache and the next build pays the
  full remap cost for all six branches.

## Offline tools

`dev.redstone_dust_order.tools.TablePngDump` exports the whole `(mode, A, B)`
space as a PNG, one pixel per entry, so a palette can be judged on paper before
building a circuit to look at in game. It lives in the test source set on
purpose — it exists to answer "is this palette readable", and that answer does
not belong in the jar.

```
java -cp <test-classpath> dev.redstone_dust_order.tools.TablePngDump out.png [palette]
```

Modes 3 and 6 come out half black: those are the `(mode, A)` pairs no real
position can reach, because `A` is the low 4 bits of `L` and those two modes only
trigger at a single `L`.

## Open questions

- **Where is the 7-element `HashSet<BlockPos>` in 1.21.1 and 26.3?** 26.x has a
  fixed `NeighborUpdater.UPDATE_ORDER` constant of `[W,E,D,U,N,S]`, which looks
  like it contradicts the model. Either the collection happens at a higher level
  than `NeighborUpdater`, or the article only describes ≤1.21.x. This does not
  affect the implementation — the algorithm is version independent — but it does
  affect how strongly the README can claim to describe vanilla, and it is why
  `/rsusoc check` exists and why it is honest about its limits.
- **Shader packs.** No verification yet on 1.19.4 or 26.x.
- **The companion "signal strength" resource pack.** Specified in the design
  notes, deliberately a separate deliverable, not built here. Its load-bearing
  assumption is that `redstone_wire`'s blockstate can match `power`, which vanilla
  never does; worth testing before writing 35 files.
- **ModMenu integration.** Not implemented. Config is currently reachable only
  through `/rsusoc` and the config file. Adding it means a ModMenu dependency per
  version; decide whether that is worth it.