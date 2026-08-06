# World corruption engine — ashen nuclear winter

The engine buries freshly generated terrain under an ashfall. Every chunk is processed **exactly
once**, when it first generates.

## The two principles, and the mistakes that produced them

This design is the fourth attempt. The first three all looked *griefed* rather than ruined, for
reasons worth recording, because each was invisible until the world was walked.

**1. Ash falls on everything. Coverage is universal; only depth varies.**

Earlier versions gated ground changes behind a patch threshold, so a fraction of columns changed and
the rest stayed vanilla. That produces exactly the pattern a griefer leaves: an intact world with
altered blocks scattered through it. There is no threshold value that fixes it, because the problem is
the *existence* of a clean/altered boundary at block scale.

Snow in a winter biome is the model: every surface gets the same treatment, and it reads as weather
because nothing was singled out. So the mantle covers essentially all ground, and the corruption level
decides how *deep* the ash lies, not *whether* it is there.

**2. Whole shapes, not block edits.**

Every earlier version *thinned* canopies — deleted a share of each tree's leaves. A vanilla tree shape
with holes punched through it is the single clearest griefing signature in Minecraft, and tuning the
share only changes how obvious it is.

So nothing is thinned. A tree is either **alive and completely untouched**, or **dead**: no leaves at
all, charred trunk, a couple of bare branch stubs for silhouette, sometimes snapped short or brought
down entirely. Dead trees look dead because their shape is a dead tree's shape.

A corollary: **the terrain drives placement.** Damage that ignores slope and elevation always reads as
painted on. `TerrainProbe` supplies slope, relief, and water proximity, so ash piles up in hollows,
lies evenly on flat ground, and blows off steep faces down to bare rock. The result looks *caused*.

## How "once" is guaranteed

1. `ChunkLoadEvent#isNewChunk()` — freshly generated terrain only. Pre-existing chunks and anything
   players built are never touched.
2. `ChunkMarker` — writes the engine version into the chunk's persistent data container, which lives
   in the region file. Survives restarts with no growing database.

The marker is written *before* the pipeline runs, so a processor that throws is logged but the chunk is
never reprocessed — stacking ashfalls would compound. Raising `ChunkMarker.ENGINE_VERSION` is the
deliberate way to re-run over an existing world.

## Layers

```
world/
  WorldEngine            gate, mark, run pipeline; caches noise fields per world
  WorldEngineFactory     the one place that wires stages and transformers
  ChunkContext           per-chunk state: snapshot reads, physics-free writes, cached masks
  ColumnMasks            lazily filled per-chunk noise samples
  terrain/
    TerrainProbe         slope, relief, water proximity — what makes damage terrain-coupled
  noise/
    OpenSimplexNoise     2D OpenSimplex, no axis-aligned artifacts
    NoiseField           octaved sampler, calibrated so thresholds are percentiles
    NoiseFields          five independent fields, built once per world
  corruption/
    CorruptionLevel      RECOVERED / SCARRED / DEVASTATED
    CorruptionProfile    ash depth and survival rules for a level, blendable
    CorruptionZone       resolves level + intensity + effective profile per chunk
  ash/
    AshPalette           the materials one biome turns into
    AshMantle            the universal covering pass — carries the whole look
  biome/
    AbstractBiomeTransformer  standard pipeline, shared by all biome groups
    <Biome>Transformer        one class per biome group: biomes + palette
  feature/
    DeadTrees, CharredWood, DeadUndergrowth, DryBeds, Craters, TreeScan
```

## Thresholds are percentiles

Summed noise octaves cluster around 0.5 — values above ~0.8 essentially never occur. An early version
set `impact-zone-threshold: 0.97` assuming a flat distribution, which meant **zero craters ever
generated**. `NoiseField` now calibrates at construction: it samples a spread grid, sorts, and maps
every read to its percentile. `0.4` genuinely means the worst 60%. The mapping is monotonic, so
continuity and determinism survive.

`detail-scale` deserves its own warning: below roughly a dozen blocks, ground materials alternate
block by block and the ground reads as confetti — the brown-shuffle failure of version three. The
default is 20.

## Measuring the result, not the wiring

`CorruptionStatisticsTest` asserts observable coverage: the share of the world at each level, that
**every level carpets over 40% of its ground**, that ash deepens towards the epicentre, that devastated
forest is under 2% alive while recovered forest is over 30% alive, and that craters actually appear.
A test that checks "the crater method was called" passes happily while producing zero craters.

## Corruption levels

`CorruptionZone` samples the corruption field at the chunk centre; the field varies over hundreds of
blocks so neighbouring chunks share a level (>90% agreement, asserted). It also carries an
**intensity** — depth into the band — and blends the profile from the level below, so the ash deepens
gradually instead of stepping at a boundary.

| Level | Look |
| --- | --- |
| `RECOVERED` | thin ash, half the forest alive, streams still run, craters very rare |
| `SCARRED` | deep ash, only sheltered groves alive, dry streams, craters in places |
| `DEVASTATED` | total cover, nothing alive, scoured ridges, drifts in every hollow, crater clusters |

## The passes, in order

Order matters: water dries before ash settles so a drained bed takes the mantle; undergrowth clears
before the mantle so ash lies on top; trees are last so a felled trunk rests on the finished surface.

- **`DryBeds`** — shallow water (≤3 deep) drains to cracked bed. Lakes and oceans stay: a dead sea
  under ash beats a canyon where the sea used to be.
- **`DeadUndergrowth`** — outside a living grove, plants are gone. Cleared plants often leave a dead
  bush, because bare swept ground reads as deletion rather than as a dead landscape.
- **`AshMantle`** — the load-bearing pass. Steep faces (`scour-slope`) strip to tuff and gravel;
  sheltered ground takes pale ash ground plus an ash carpet; hollows drift deep enough to raise the
  ground a block. Material choice comes from the detail field, so materials form broad areas.
- **`DeadTrees`** — whole canopies removed outside living groves; trunks charred to polished basalt
  (vertical grain reads as a burnt trunk), branch stubs added, some snapped, some collapsed with the
  trunk laid in the ash and a stump left standing.
- **`Craters`** — ordinary impacts, only inside impact zones so they cluster. Ragged rims, ejecta
  apron, fluids never carved. Debris includes seams of coal and copper, occasionally iron, torn out of
  the shallow rock.
- **`HugeCraters`** — the rare enormous impacts, 18-36 blocks in radius, spanning many chunks. These
  are the events the rest of the world is reacting to, so they are also the richest: iron, gold,
  redstone, lapis, raw metal blocks, and rarely diamond, exposed on the floor and thrown out with the
  ejecta.

## Crossing chunk borders: how a huge crater works

An ordinary crater fits inside one chunk, so it can be rolled per chunk. A crater wider than a chunk
cannot: every chunk it touches has to agree on exactly where the centre is and how wide it is, or the
bowl comes out as mismatched fragments.

`CraterSites` solves it without ever loading a neighbour. Sites live on a coarse grid in world
coordinates and are derived by hashing the cell, so they are a pure function of the world seed. Each
chunk looks at the 3×3 cells around it, keeps the sites that reach it, and carves only its own slice.

The bowl is stamped as a **depth profile relative to each column's own ground**, not as a sphere around
an absolute point: a column only needs its distance from the centre to know how deep to cut. Two chunks
carving their halves independently therefore produce one continuous bowl — and the crater follows the
terrain it landed in instead of hovering at a fixed height.

Sites are only kept where the region is already `DEVASTATED`. Resolving that is pure arithmetic over
the noise fields, so the check can ask about a location hundreds of blocks away for free. The result:
the largest impacts sit inside the worst land, reading as the reason that region is the way it is.

## Ground height, and the bug that hid a forest

`ChunkContext.surfaceY` returns the highest block in a column. Under a tree that is a **leaf**, and the
first version of the ash mantle read the surface from there, found a material it was not allowed to
touch, and returned. Every column beneath a canopy kept its vanilla ground: a dense birch wood came out
completely unchanged. `TerrainProbe` had the same fault, computing slope from canopy heights, so flat
woodland looked like cliffs and got scoured.

`ChunkContext.groundY` walks down past canopy, trunks, and undergrowth to the real floor, stopping at
water so nothing acts underwater. Every terrain-facing pass uses it. `TreeScan` and `DeadUndergrowth`
still start from `surfaceY`, because they genuinely need the whole column from the canopy down.

## Biome identity

Every biome shares the ash carpet — that shared layer is what makes it one event rather than many
edits. Underneath, each `AshPalette` differs: taiga goes to tuff and deepslate, swamp to grey mud,
desert keeps its sand under the carpet, forest to dust-grey dirt path. Sand and stone are never
converted to soil; the biome is buried, not replaced.

## Adding a biome group

Extend `AbstractBiomeTransformer`, declare `name()`, `biomes()`, `palette()`, override `hasTrees()` if
needed, and list it in `WorldEngineFactory`. About 40 lines, no feature ordering duplicated. Unclaimed
biomes fall through to `DefaultTransformer`.

## Adding a future system

Radiation, restoration, ruins, loot, structures: implement `ChunkProcessor`, append it in
`WorldEngineFactory`. You inherit gating, marking, snapshot reads, physics-free writes, cached masks,
the terrain probe, and the corruption zone.

## Performance

One `ChunkSnapshot` per chunk; one `TerrainProbe` pass over 256 columns; one `TreeScan` feeding all
tree work; `ColumnMasks` fills a field's samples on first use only; noise fields built per world and
cached; every write physics-free and skipped when the material already matches; scanning bounded to
`scan-depth`.

## Known limits

- Below `scan-depth` (deep ravines, cave systems) is untouched.
- Runs synchronously on the chunk-load thread. Fine for exploration; a mass pregen will feel it.
- Not Folia-aware.
- Craters and terrain probing stop at chunk borders by design, so nothing forces a neighbour to load.
  Edge columns treat themselves as flat for slope purposes.
- `org.bukkit.block.Biome` constants initialise from the server registry, so biome→transformer mapping
  cannot be unit tested; it is exercised at runtime.
