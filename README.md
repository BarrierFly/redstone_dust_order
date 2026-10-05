# Redstone Dust Update Order · 红石粉更新顺序

Colours every redstone dust block by **the order vanilla updates its seven
neighbours in**, so the otherwise invisible `HashSet` iteration pattern becomes
visible as a texture in the world.

Client-side only. No protocol, no server-side install, no packet.

---

## What you are looking at

vanilla collects the seven "update cores" of a dust position — the block itself
plus its six neighbours — into a `java.util.HashSet<BlockPos>`, always inserted
in the order `self, -Y, +Y, -Z, +Z, -X, +X`. A `HashSet` iterates bucket-ascending,
insertion-order within a bucket. The resulting order is *not* the same everywhere
in the world, and it is not something you can see.

This mod gives each of the resulting orders a colour, so a flat stretch of dust
turns into a readable 3D pattern. Build a long dust line and you can see where the
update order flips along it.

**Turn it on:** it ships off. Bind a key under *Options → Controls → Miscellaneous*
(`redstone_dust_order.key.toggle`), then press it. Or use `/rsusoc on`.

## Compatibility

| | |
|---|---|
| Loader | Fabric |
| Versions | 1.19.4, 1.20.1, 1.21.1, 1.21.10, 1.21.11, 26.3 |
| Side | client only |
| Blocks affected | `minecraft:redstone_wire` and nothing else |

Redstone torches, repeaters and comparators are deliberately untouched.

## Palettes

Three built in, switchable at runtime. The default is the readable one.

| | |
|---|---|
| **High contrast** (default) | 6 loud, far-apart colours for the orders covering ~97% of all blocks, plus a quiet grey-blue family for the rest. |
| **3 group** | The browser simulator's mode: 3 hues × 2 shades for the six main patterns, one grey for everything else. |
| **Update order** | The simulator's Fourier embedding — all 60 orders get their own colour. Faithful, but **exploration only**: some of those 60 colours are only ~8 apart in RGB, which no thin dust line will ever resolve. |

Why the default is not the pretty one: the design notes measured the Fourier
palette and found a pair of orders whose colours differ by 9/255 — one of them
covering 24.3% of all blocks. Picked by hand and then *verified* over every
uniform gain vanilla applies to the neutral dust texture (grey levels
254/235/217 → gains 0.996/0.920/0.851), the default palette's worst pair is 112.8
apart. There is a test that fails if anyone edits it into mush.

## Alpha

`alpha = 1` (default) replaces the vanilla colour entirely. Anything below 1
blends: `mix(vanillaPowerColour, orderColour, alpha)`. This is how you keep some
sense of signal strength while still reading the pattern. `0` is pure vanilla.

## `/rsusoc`

```
/rsusoc                      current settings, order count, config path
/rsusoc on | off             toggle
/rsusoc palette <name>       high_contrast | group3 | fourier
/rsusoc alpha <0.0 - 1.0>    blend factor
/rsusoc check                re-derive the model for where you are standing
```

`check` is the interesting one. It recomputes the seven buckets and the traversal
straight from the arithmetic — no lookup table involved — confirms the table
agrees, and prints the whole derivation for your position so you can compare it
against the browser simulator at the same coordinates.

**What `check` does not do:** it cannot verify that vanilla really routes dust
updates through a `HashSet`. That would need a mixin and a packet sniffer, and
this mod ships neither on purpose. What it verifies is that the table still
matches the model it was generated from. See *Honest limits* below.

## How it renders

By replacing the block tint of `minecraft:redstone_wire`, and only that.

The redstone dust textures are pure neutral grey — the 3D shape comes entirely from
the tint — so swapping the tint is the entire job. No models, no textures, no
atlas, no render layer, no shader. That is why it works with **any** resource pack
and shader pack: it never touches the files they replace. F3+T behaves correctly.

Registration goes through vanilla's own `BlockColors#register`, not Fabric's
`ColorProviderRegistry.BLOCK`. The Fabric API wraps rather than replaces whenever
vanilla already has a provider for the block, which is exactly this case, so it
would not have overridden anything. Vanilla's registry bottoms out in a plain
`List#set`, so registering after `createDefault()` just overwrites.

**Consequence:** vanilla encodes signal strength in the same tint, so taking the
tint over means losing the power readout unless you use `alpha` or a resource
pack. The optional companion pack that restores it via the transparent
`redstone_dust_overlay` layer is a separate deliverable and is not in this repo.

A tint change is baked into chunk vertex colours, so the mod marks itself dirty
and does exactly one rebuild on the next client tick. It never rebuilds from
inside a render frame — that would re-mesh every chunk on every frame forever.

## The model in one screen

```
hashCode = x + 31y + 961z          // Vec3i.hashCode; BlockPos does not override it
hash     = hashCode ^ (hashCode >>> 16)
bucket   = 15 & hash
```

Only three numbers of `hashCode` matter:

- `L` = low 16 bits
- `A` = low 4 bits
- `B` = bits 16..19

A core's neighbour hash is `h + delta` for a fixed `delta`, so its bucket is
`((A + delta) & 15) ^ ((B + carry) & 15)`, where `carry` is −1/0/+1 depending on
whether the neighbour's low half left the 0..65535 window. Which cores do that
depends only on `L`:

| `L` | mode | affected |
|---|---|---|
| 0 | 6 | borrow `{-Z,-Y,-X}` |
| 1 – 30 | 5 | borrow `{-Z,-Y}` |
| 31 – 960 | 4 | borrow `{-Z}` |
| 961 – 64574 | 0 | safe |
| 64575 – 65504 | 1 | carry `{+Z}` |
| 65505 – 65534 | 2 | carry `{+Z,+Y}` |
| 65535 | 3 | carry `{+Z,+Y,+X}` |

So `(mode, A, B)` — 7 × 16 × 16 = **1792** combinations — pins down all seven
buckets and therefore the whole traversal. That is the entire lookup table, built
once at startup and read-only thereafter: four integer operations and two array
loads per block, no branches, no floating point, no allocation.

The table stores an **order index**, not a colour. Switching palette or alpha is
then free, and needs no rebuild of anything but the chunk meshes.

### What that gives

Sweeping all 2²⁰ `(B, L)` classes turns up exactly **60** distinct orders:

| order | traversal | share |
|---|---|---|
| 0 | `-Y → +Z → +X → O → +Y → -Z → -X` | 24.26682% |
| 1 | `+Y → -Z → -X → O → -Y → +Z → +X` | 24.26682% |
| 2 | `-Y → +Z → +X → +Y → -Z → -X → O` | 12.13341% |
| 3 | `+Y → -Z → -X → -Y → +Z → +X → O` | 12.13341% |
| 4 | `O → -Y → +Z → +X → +Y → -Z → -X` | 12.13341% |
| 5 | `O → +Y → -Z → -X → -Y → +Z → +X` | 12.13341% |
| 6 – 59 | … | 2.93% total |

Six orders, ~97%. The rest live in a thin strip along X where the hash crosses a
16-bit boundary — about 1922 blocks long in X, 62 in Y, 2 in Z, so in practice you
see it as occasional long stripes.

The six common orders are exactly the ones with a clean structure: the dust block
itself plus two groups of three (`{-Y,+Z,+X}` and `{+Y,-Z,-X}`) that share a bucket
each. Where that holds, the traversal is "three contiguous groups", and the only
thing that varies is which group comes first. That is the *3 group* palette, and
its six colours. Which is also why the ~97% figure is exactly the safe-mode share.

### Two corrections to the design notes

Both were found by checking rather than by reading, and both are pinned by tests:

1. **The pattern is not strictly 16-periodic.** `A` is, exactly — a 16-block shift
   along any axis adds 16, 496 or 15376 to the hash, all multiples of 16. But the
   buckets also depend on `B`, and translating along Y or Z shifts the hash by 496
   or 15376, not 16, so for large `L` that pushes the hash past the point where a
   neighbour starts carrying and the order changes. It holds comfortably away from
   that boundary and breaks near it.

2. **A single 16³ cell shows only 3 of the 6 common orders**, not all 6. `B` is
   constant inside one cell, and the order depends on `B`. You have to sample
   across cells to see all six.

## Honest limits

- The whole model rests on vanilla routing dust updates through a 16-bucket
  `HashSet` keyed by `Vec3i.hashCode()`. If a future version changes that, this
  mod fails **silently** — it will keep colouring confidently and be wrong. Use
  `/rsusoc check`, and compare against the simulator.
- The `redstone_experiments` feature pack (26.x) randomises update order outright.
  The mod detects it and warns once in chat, and `/rsusoc check` reports it, but it
  does **not** disable itself — quietly changing what a player sees is worse than
  telling them it is meaningless.
- 26.x also has a fixed `NeighborUpdater.UPDATE_ORDER` constant that looks like it
  contradicts the `HashSet` model. This has not been traced to a resolution; see
  *Open questions* in `AGENTS.md`.
- Shadows/lighting on 26.x have not been verified on real hardware.

## Credits

The mechanism and the mathematics come from
[hotpad100c / Ryan100c, 《红石粉更新的数学规律》](https://hotpad100c-github-io.pages.dev/posts/RedstoneDustMath/),
and the two simulator-derived palettes are ported from
[红石粉更新顺序模拟器](https://hotpad100c-github-io.pages.dev/public/dust_simulate).
This mod is "the browser simulator, moved into the game", with a palette designed
for actually reading it.

Project skeleton from [`fabric-mod-template`](https://github.com/Fallen-Breath/fabric-mod-template).

Licensed under the [Unlicense](LICENSE). The referenced articles and the simulator
are someone else's work; nothing was copied verbatim from them.