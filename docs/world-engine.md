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
  WorldEngine            gate, mark, run pipeline; caches the history engine per world
  WorldEngineFactory     the one place that wires stages and transformers
  ChunkContext           per-chunk state: snapshot reads, physics-free writes, cached masks
  ColumnMasks            lazily filled per-chunk noise samples
  Vegetation             what counts as a plant, and what cannot stand on nothing
  history/               ← what happened here, before the player arrived. No Bukkit imports.
    HistoryEngine        one per world; resolves a RegionProfile for any coordinates
    RegionProfile        the single shared answer every system reads
    RegionStory          FRONT_LINE / RUINED_TOWNS / ASHEN_WASTE / GREEN_REFUGE / DUST_BOWL
    HistoryMap           the contract: deterministic, percentile, 512+ blocks
    WarMap               where things were destroyed
    AshfallMap           where things were buried
    RestorationMap       where life held on, plus the sharp pockets that carry contrast
    HistoryMaps          registry of the layers, built once per world
    HistoryShaping       history → the numbers every existing pass already reads
    LandmarkMap          deterministic siting; LandmarkType, Landmark
  infrastructure/        ← what was built between the places, before anything was built on it
    InfrastructureEngine where the routes are; what every later system asks
    RouteNetwork         the Gabriel graph: which places are joined
    RoutePath            the curve between two places, sampled for drawing
    Route, RouteType     a connection, and what kind
    Roadbed, PowerLine   surface, headroom, bridges, pylons
    InfrastructureProcessor  the pipeline stage, run FIRST
  terrain/
    TerrainProbe         slope, relief, water proximity — what makes damage terrain-coupled
    LandLookup           land or open water at a position, asked of the generator, not the chunk
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
    Blast                what an impact takes with it: nothing may be left standing over a hole
    HangingPlants        a pipeline stage of its own, run last: prunes what is left adrift
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

## What happened here — the simulated history

The level above says *how badly* a place was hit. The history layer (`world/history/`) says **what
happened to it**, which is what stops every devastated region from looking like every other one. It is
also the foundation the rest of the plugin is meant to grow from: ruins, roads, loot, radiation, and
settlements should all derive from the same simulated past rather than each rolling its own dice, since
that shared source is the only way a procedural world ends up internally consistent.

Three independent layers, all far wider than anything else in the engine:

| Layer | Default scale | What it does |
| --- | --- | --- |
| `WarMap` | 1024 | **destroys** — craters, snapped and flattened deadfall, military landmarks |
| `AshfallMap` | 768 | **buries** — deep ash, drift, dry watercourses. Breaks nothing |
| `RestorationMap` | 640 | **spares** — surviving groves, thinner ash. Not the inverse of war |

Their combination is the region's story:

| Story | War | Ash | Reads as |
| --- | --- | --- | --- |
| `FRONT_LINE` | high | high | crater fields, burned forest, military wreckage |
| `RUINED_TOWNS` | high | low | broken towns still legible, roads, scattered wreckage |
| `ASHEN_WASTE` | low | high | deep ash, standing dead trees, near silence |
| `GREEN_REFUGE` | low | low | surviving forest and green hollows (needs restoration too) |
| `DUST_BOWL` | low | low | abandoned farmland, dry plains, dead villages |

**Keep the three scales unequal, and not multiples of each other.** Equal scales peak and trough
together, and the world collapses into a handful of enormous uniform districts. Unequal ones beat
against each other: measured over a straight walk, the story changes every ~290 blocks with the
defaults, while no single layer changes anywhere near that often.

`RestorationMap` carries a second, small, sharply bounded field on top of the broad one. Those
**pockets** are the mechanism behind the contrast the world is built on — a surviving grove inside a
burned forest, a green hollow in a crater field. Broad noise cannot do it: a layer wide enough to
define a region is far too wide to put anything small inside one.

`HistoryShaping` bends the numbers every pass already reads, rather than adding branches inside them,
so a pass written next year is story-driven for free. Continuous values drive the terrain, so no story
boundary ever steps; the `RegionStory` enum exists only for the discrete choices content systems have
to make. Measured over 22,500 chunks with the default seed:

| Story | Share | Ground in an impact zone | Deep ash | Trees flattened | Groves alive |
| --- | --- | --- | --- | --- | --- |
| `FRONT_LINE` | 16% | 56% | 0.44 | 0.44 | 0.20 |
| `RUINED_TOWNS` | 24% | 56% | 0.30 | 0.44 | 0.19 |
| `ASHEN_WASTE` | 25% | 18% | 0.44 | 0.30 | 0.37 |
| `GREEN_REFUGE` | 18% | 16% | 0.22 | 0.29 | 0.46 |
| `DUST_BOWL` | 17% | 18% | 0.35 | 0.31 | 0.28 |

Two invariants are enforced in code: ash coverage can never fall below `HistoryShaping.MIN_CARPET`, so
no story — however green — leaves ground vanilla; and setting all three `*-influence` values to zero
restores the pre-history engine exactly, which makes them the honest off switch.

Whether craters exist at all is **war's** decision, not the corruption level's. Density alone left the
corruption field — which knows nothing about any war — as the real decider of where the bombardment
was, and only measuring the world revealed it.

## Landmarks

Rare places worth setting out for: missile silo, military base, research facility, airport, hospital,
hydroelectric dam, radio tower. **Locations only — nothing is built on them yet.**

Sites sit on a 1500-block grid (validated to 1000–3000), 85% of cells occupied, each site wandering
only within the middle 40% of its cell so two sites either side of a border can never end up
neighbours. Measured: nearest-neighbour distances of 942 / 1285 / 1752 blocks (min / mean / max), and
nowhere is further than 3000 blocks from something to walk to.

The type is chosen from the history at the site, so a silo lands in a war zone and a dam in a green
valley — a landmark should be evidence of what happened, not decoration dropped on top of it. Nothing
here reads the terrain: that would need chunks loaded, and a landmark has to be knowable from
arbitrarily far away. A generator that needs a river under its dam can refuse the site when it arrives.

Reach them through `RegionProfile.landmark()` (standing on one) and `nearestLandmark()` (what a road
should aim at). In game, `/nec locate <kind>` finds the nearest of any type — searching one ring of
cells at a time and then one ring further, because a site in the corner of a ring can be further away
than one just inside the next.

## Infrastructure, and why it comes first

Cities do not appear at random coordinates. They grow where roads meet, industry sits where the rail
reaches, and a checkpoint belongs where a road crosses a river. So the network is laid down **before
anything else in the pipeline**, and everything built later can ask where it is.

That query — `InfrastructureEngine.nearestRoute(x, z)` — is the actual product of this layer. Roads
drawn to buildings that were placed first always look drawn.

**Which places connect** is a Gabriel graph: two places join when no third place lies inside the circle
that has the pair as its diameter. It contains the minimum spanning tree, so nothing is ever stranded;
it never crosses itself; and it branches into the roughly-triangular mesh a real road map settles into.
Joining every pair in range gives a cobweb, joining only nearest neighbours gives islands.

**What runs between them** comes from what they are — a works needs rail, a dam is the far end of a
power line, everything else is a road:

| Endpoints | Route |
| --- | --- |
| anything with a research facility | railway |
| dam ↔ radio tower | power line |
| everything else | highway |

**Heights.** Routes hug the terrain. That is not laziness: a column's own ground height is something the
chunk can see, neighbouring columns differ by a block at most, so a road drawn column by column comes
out continuous even though no chunk ever saw the next one. An engineered grade — the embankments and
cuttings of a real motorway — needs the terrain kilometres ahead, which is exactly what cannot be known
here. Water is the exception, and it is what makes bridges work: a water surface is flat and every
column of it reports the same height, so a deck two blocks above it is at the same height for every
chunk touching the crossing.

**Two determinism traps**, both invisible in the code and both fatal:

1. The Gabriel test must take its witnesses from **the pair's own midpoint**, never from what the
   observer can see. Otherwise a chunk to the west sees a blocking landmark that a chunk to the east
   does not, and the road exists on one side of the border and not the other.
2. The candidate window must be **derived from `max-route-length`**. Look at too few cells and a chunk
   near one end of a long route never sees the far end, never considers the pair, and draws nothing —
   while every chunk in the middle draws a road. This one was found by a test, not by reading.

Measured on the default seed: **53 routes per 36 km²** (37 highway, 9 railway, 7 power line), 12% of
chunks carrying one, a mean walk of **189 blocks** to the nearest road, and **93 µs** to resolve a
chunk's routes.

An early version used a bounding box to decide whether a route came near a chunk. The box around a long
diagonal covers ground the route never approaches, so three quarters of all chunks believed they had a
road and paid to find out otherwise — a millisecond per chunk. Ruling pairs out against the straight
line first, then walking every eighth sample, cut it by 22×.

### Runways, and where an engineered grade *is* possible

Roads riding over hills is correct — a real road follows its terrain. A runway is the opposite kind of
object: an aircraft cannot land on a gradient, so the ground gives way to the structure instead.

That is possible for a **site** even though it is impossible for a route. A route is thousands of blocks
long and levelling one needs terrain knowledge kilometres ahead. An `Airfield` is a fixed rectangle at
fixed coordinates, so its platform height is a pure function of the seed — anchored to
`World.getSeaLevel()`, the one global height a world gives away for free — and every chunk touching it
computes the same number without asking anyone. `Earthworks` then cuts and fills each column to it.

Measured across three seeds: **260 × 18 strips** with a 4-block levelled shoulder either side, one
height each (63–72, sea level plus a hashed lift), bearings spread across the compass rather than
axis-aligned, ~6760 levelled columns per airfield. `AIRPORT`'s footprint went from 64 to **180** so the
runway fits inside the site that owns it.

The fill is capped at `Earthworks.MAX_FILL`. Unbounded, a runway ending over a valley would pour a
hundred-block plinth of tuff; a hole under the far end reads as subsidence, which this world has plenty
of. Cuts are not capped — a genuine cutting through a hillside is what building an airfield in hills
actually costs.

Levelling the strip is only half of an airport. An aircraft has to get down onto it and back off
again, so anything poking above a surface that rises one block per 7 outward — `approach-reach` blocks
past the strip in every direction — has its top taken off. Real airfields call this the obstacle
limitation surface, and it is why an airport is a wide flat *place* rather than a flat line. Below the
surface nothing is touched, so a valley beside a runway stays a valley.

**Not built yet:** terminals, hangars, and aprons are structures rather than infrastructure. True
tunnels and canals still need a *route*-scale engineered grade, which the site trick does not solve.
Junctions are overlaps rather than interchanges. Railways still follow terrain, which real rail cannot
do at steep grades — the same site trick will not fix that, since a railway is a route.

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
- Both crater passes cut *downwards* — a depth stamped from each column's ground, or a sphere that
  barely clears the surface. Neither looked at what stood above, so a crater opened under a forest and
  left the forest floating over the hole: trunks, canopy, ash carpet, all of it. `Blast` now takes
  whatever was standing on a carved column with it. An impact takes the trees; that is the whole
  reading of a crater.
- **`HangingPlants`** — a pipeline stage of its own, running **after** all of the above. Vines hold on
  to what is beside or above them, and physics is disabled, so stripping a canopy leaves a curtain of
  green hanging out of open sky. This sweeps the finished chunk and prunes anything left adrift. It is
  a separate final stage rather than a check inside each stripping pass, so it also covers the
  removing passes nobody has written yet.

## Plants that are not what they look like

Three plant facts each caused a visible artefact, and all three now live in `Vegetation`:

- **A plant can be two blocks tall**, and the snapshot height map counts *neither* half — it reports
  the highest motion-blocking block, and nothing you can walk through qualifies. A pass looking one
  block above the surface found a sunflower's stem and never its head, so stems vanished and heads hung
  in the air. `Vegetation.REACH` is how far up a pass has to look.
- **Some plants are solid blocks.** Bamboo, cactus, and a huge mushroom's cap all report solid, so the
  ground search stopped at the *top of the plant* and called it the floor: ash settled on top of bamboo
  stalks, and mushroom caps turned half to dirt. `isStanding` names them; the ground search walks past
  them and no pass writes into one. They are left whole — a bamboo thicket standing untouched in an ash
  field beats one with a dirt lid.
- **Grass is removed everywhere**, living groves included. Green tufts through an ash field are the
  loudest remaining sign that ground was edited rather than buried.

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

Sites are kept only where **the war map says the fighting reached**, so the largest impact and the
ruined country around it are one event rather than two coincidences. Resolving that is pure arithmetic
over the noise maps, so the check can ask about a location hundreds of blocks away for free.

They are also kept only where the generator puts **land**. Three quarters of the first craters anybody
walked to were at sea, and a crater at sea carves nothing at all — fluid columns are skipped — so it
spent the rarest feature in the world on a kilometre's walk to look at open water. `LandLookup` asks
the *biome*, via `getComputedBiome`, which is what the generator would produce and needs no chunk
loaded; asking the terrain would break the agreement that lets one bowl cross chunk borders.

That gate costs roughly a third of the sites, which is why `huge-craters.chance` defaults to `0.6`
rather than the `0.4` it started at: the number of craters a player can actually walk to stays about
what it was when they were still allowed to land in the sea and carve nothing. Raise it further for
more of them, or lower `spacing` to bring them closer together — never loosen the gate.

`LandLookup` is an interface rather than a direct call for a reason worth remembering: `Biome` cannot be
touched outside a running server — its constants initialise from the registry, and Mockito cannot even
mock it — so a class that names it cannot be tested at all. The seam keeps the deciding code testable
and confines the untestable part to one factory method.

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
and the terrain probe.

**Ask `context.region()` what happened here rather than sampling your own noise.** That is the whole
point of the history layer: a system that rolls its own dice will contradict the ash, the trees, and
the ruins in the same valley, and no amount of tuning fixes an inconsistency of that kind. Branch on
`story()` for discrete choices, read `warIntensity()` / `ashfall()` / `restoration()` when you want a
continuous response, and use `landmark()` / `nearestLandmark()` for anything with somewhere to go.
`HistoryEngine` can resolve any coordinates, including ground no player has loaded, so a road or a
settlement can plan beyond the chunk it is standing in.

## Performance

One `ChunkSnapshot` per chunk; one `TerrainProbe` pass over 256 columns; one `TreeScan` feeding all
tree work; `ColumnMasks` fills a field's samples on first use only; every noise field built once per
world and cached inside the `HistoryEngine`; every write physics-free and skipped when the material
already matches; scanning bounded to `scan-depth`.

Resolving a `RegionProfile` costs **~6 µs per chunk** measured on the default config — a handful of
noise samples plus nine integer hashes for the landmark cells, walked once and used for both landmark
questions. Against the thousands of block writes in the same chunk it is noise, which is why the
history engine holds no cache and no mutable state: a pure function needs neither, and both would be a
correctness risk on a server generating chunks from more than one thread.

## Known limits

- Below `scan-depth` (deep ravines, cave systems) is untouched.
- Runs synchronously on the chunk-load thread. Fine for exploration; a mass pregen will feel it.
- Not Folia-aware.
- Craters and terrain probing stop at chunk borders by design, so nothing forces a neighbour to load.
  Edge columns treat themselves as flat for slope purposes.
- `org.bukkit.block.Biome` constants initialise from the server registry, so biome→transformer mapping
  cannot be unit tested; it is exercised at runtime.
