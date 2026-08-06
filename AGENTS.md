This repository is **NewEraCore**, a PaperMC/Spigot core plugin for the New Era server.

## Architecture

**Entry point**: `NewEraCore extends JavaPlugin` (`com.edysmajler.neweracore`). Bukkit/Paper calls `onEnable()` and `onDisable()` during the plugin lifecycle. `onEnable()` loads and validates `config.yml`, then opens the SQLite-backed data store and starts its auto-flush task; `getPluginConfig()` and `getStore()` expose both to feature code. In `plugin.yml`, `${PACKAGE}.${NAME}` identifies the main class. The `processResources` task fills these placeholders at build time from `group` in `build.gradle.kts` and `rootProject.name` in `settings.gradle.kts`, so the class name must stay equal to `rootProject.name`.

**JAR packaging**: The standard `jar` task is disabled, and `shadowJar` is the sole output. It shades CommandAPI and relocates it to `<group>.commandapi`. CommandAPI is excluded from `minimize()` because it loads classes through reflection. `assemble` depends on `shadowJar`.

**cw-commons dependency**: [cw-commons](https://github.com/CrimsonWarpedcraft/cw-commons) provides `Command`/`BaseCommand` for command registration, `Config`/`BukkitConfigManagerBuilder` for YAML loading and Jakarta validation, and `BukkitDataStoreBuilder`/`Repository`/`PlayerDataManager` for persistent player data. These classes are not defined locally. The project consumes cw-commons from JitPack as `com.github.CrimsonWarpedcraft:cw-commons`. `build.gradle.kts` pins a tagged release, such as `v0.3.0`, instead of the unstable `main-SNAPSHOT`. Update that tag deliberately.

Jackson and Hibernate Validator remain direct dependencies because `PluginConfig` uses their `@JsonProperty` and `@NotBlank` annotations. cw-commons exposes these libraries as transitive, unbundled `api` dependencies so each consumer can shade and relocate its own copy without classloader conflicts. The direct declarations are redundant for dependency resolution, but they prevent this project from relying on another project's transitive exposure choices.

**World corruption engine** (`world/`): `WorldEngine` listens for `ChunkLoadEvent` and corrupts each chunk exactly once. Two gates enforce that: `isNewChunk()` limits work to freshly generated terrain, and `ChunkMarker` stamps `ChunkMarker.ENGINE_VERSION` into the chunk's persistent data container, which persists in the region file. The marker is written *before* the pipeline runs, so a failing processor cannot leave the chunk eligible for a second, compounding pass. Raising `ENGINE_VERSION` is the deliberate way to reprocess existing chunks.

The theme is an ashen nuclear winter, and two principles are load-bearing. Both came from shipped versions that looked *griefed* rather than ruined:

1. **Ash falls on everything; only depth varies.** Gating ground changes behind a coverage threshold leaves an intact world with altered blocks scattered through it — the griefer pattern, unfixable by tuning. `AshMantle` covers essentially all ground, and the level decides depth. `CorruptionProfile.DUSTING` enforces this in code: even the calmest end of the recovered band blends from a real ashfall, never from pristine, so no chunk anywhere is left vanilla.
2. **Whole shapes, not block edits.** Never thin a canopy. `DeadTrees` removes *all* leaves outside living groves and rebuilds a dead silhouette — charred basalt trunk, branch stubs, sometimes snapped or collapsed with the trunk laid in the ash. A vanilla tree shape with holes in it is the clearest griefing signature in the game.

A corollary: placement is terrain-coupled. `TerrainProbe` (`world/terrain/`) supplies slope, relief, and water proximity, so ash drifts into hollows and strips off steep faces. Damage that ignores the landform reads as painted on. Edge columns treat themselves as flat rather than reading across a chunk border.

Placement never comes from a per-block dice roll. Coherent OpenSimplex noise (`world/noise/`) decides where things go; `NoiseFields` holds five independently salted fields (corruption, patch, blight, impact, detail) built once per world and cached by `WorldEngine`. `NoiseField` calibrates itself so **thresholds are percentiles** — an early version set thresholds above 0.8 on a bell-shaped field and generated exactly zero craters. `detail-scale` must stay well above ~12 or materials alternate block by block and the ground reads as confetti. The seeded `Random` is only for texture inside an already chosen area.

`CorruptionZone` (`world/corruption/`) assigns a `CorruptionLevel` (`RECOVERED`, `SCARRED`, `DEVASTATED`) from the corruption field at the chunk centre, plus an intensity that blends the `CorruptionProfile` towards the level below so boundaries do not step.

**Simulated history** (`world/history/`): the layer everything else is derived from, and the reason this is no longer just a terrain transformer. `HistoryEngine` (one per world, cached by `WorldEngine`) answers a single question — *what happened here?* — as an immutable `RegionProfile`: corruption level and intensity, the three history values, the `RegionStory`, the shaped `CorruptionProfile`, and any `Landmark`. Every pass reads it through `ChunkContext.region()`; nothing samples a large-scale field for itself, because two systems doing that in the same valley contradict each other. Ruins, roads, loot, radiation, and settlements are all meant to consume this same profile.

Three `HistoryMap`s at 512–2048 block scales: `WarMap` destroys (craters, flattened deadfall, military landmarks), `AshfallMap` buries (deep ash, drift, dry beds — it breaks nothing), `RestorationMap` spares. **Keep the three scales unequal and not multiples of each other**; equal scales peak together and the world collapses into a few vast uniform districts. `RestorationMap` also carries small sharp *pockets* — the mechanism behind a living grove inside a burned region, which broad noise cannot produce because a layer wide enough to define a region is too wide to put anything small inside one. Add a map by implementing `HistoryMap`, registering it in `HistoryMaps`, and exposing what it decides on `RegionProfile`.

`HistoryShaping` is the join: it bends the numbers every existing pass already reads, rather than adding branches inside them, so a pass written next year is story-driven for free. Continuous values drive terrain (no story boundary ever steps); the `RegionStory` enum is only for the discrete choices content systems must make. Two invariants live there: ash coverage is floored at `MIN_CARPET` so no story can leave ground vanilla, and all three influences at zero restores the pre-history engine exactly. Whether craters exist at all is war's decision, not the corruption level's — measuring the world was the only way to see that crater *density* alone left the corruption field, which knows nothing about any war, deciding where the bombardment was.

`world/history/` imports no Bukkit, deliberately: the whole simulation is samplable and testable without a server, which is what makes `HistoryStatisticsTest` and `LandmarkMapTest` able to assert the world's variety — story shares, how often a walk crosses into somewhere different, green surviving inside war zones, landmark spacing — instead of asserting wiring. Keep it Bukkit-free.

**Infrastructure** (`world/infrastructure/`): what was built between the landmarks, laid down *before* anything else in the pipeline. `InfrastructureEngine` answers where the routes are; the point is not the roads themselves but that every later system can ask — a town that knows a highway runs past can face it, a depot can back onto the rail, a checkpoint belongs where a road crosses a river. Buildings placed first and roads drawn to them afterwards always look drawn.

Which places connect is a **Gabriel graph** (two places join when no third sits inside the circle on their diameter): it stays connected, never crosses itself, and branches like a road map. What runs between them comes from `LandmarkType.connectsBy()` — rail to a works, a power line between dam and mast, a highway for everything else. Routes are quadratic Béziers, so a chunk recomputes the whole curve from the two endpoints and its slice lines up with its neighbours' exactly.

Two determinism traps live here, both invisible in code and both fatal to a road. The Gabriel test must take its witnesses from **the pair's own midpoint**, never from what the observer can see, or the road exists on one side of a chunk border and not the other. And the candidate window must be **derived from `max-route-length`**, not picked by eye, or a chunk near one end of a long route never considers the pair and silently draws nothing while every chunk in the middle draws a road. `RouteNetworkTest` covers both.

Routes hug the terrain — the only rule continuous across borders without cross-chunk reads. Water columns become bridges at the water surface + 2, which is flat across a whole body and therefore also continuous; that is the one piece of engineered grade available for free. Roads run before the ashfall and `ChunkContext.reserve()` stops the mantle repaving them, so ash settles *over* the tarmac and craters take bites out of it.

`Vegetation` (`world/`) holds every rule about plants, because each one was learned from a shipped artefact. A plant can be two blocks tall and the snapshot height map counts neither half, so a pass must look `Vegetation.REACH` above the surface or it strips a sunflower's stem and leaves the head. Grass is removed everywhere, living grove included. Some plants are *solid* — bamboo, cactus, huge mushroom caps — which made the ground search stop at the top of the plant and call it the floor, so ash landed on bamboo and half a mushroom turned to dirt; `isStanding` names them and they are left whole. Hanging plants need an anchor, not a footing, and `HangingPlants` prunes the ones left adrift.

Two rules about removal, both learned from craters. `Blast` takes whatever was standing on a column an impact carved — both crater passes cut downwards only, so a crater under a forest left the forest floating over the hole. And huge crater sites are gated on `LandLookup`, because three quarters of the first ones anybody walked to were at sea, where fluid columns are skipped and nothing is carved at all. `LandLookup` is an interface rather than a direct biome call for a specific reason: naming `org.bukkit.block.Biome` makes a class untestable — Mockito cannot even mock it — so the Bukkit call is confined to one factory method and everything that *decides* stays testable. Prefer that pattern for any future terrain question.

`WorldEngineFactory` wires the `ChunkProcessor` pipeline and the transformer list; new systems go there. `HangingPlants` must stay **last** in that pipeline: it cleans up after anything that removes a block, so it has to see the finished chunk. A sweep at the end catches strippers nobody has written yet, which a check bolted onto each removing pass cannot. `AbstractBiomeTransformer` holds the shared pass order — dry beds, undergrowth, ash mantle, then trees and craters — so a biome group needs only its biome set, its `AshPalette`, and the odd flag. The shared ash carpet across all palettes is what makes it one event; the materials underneath keep biome identity.

`CorruptionStatisticsTest` asserts observable coverage, not wiring. Both historical failures shipped looking correct and were only catchable by measuring how much of the world actually changed. Keep that test honest when tuning.

Unit tests cannot touch `org.bukkit.block.Biome` — its constants initialise from the server registry, so loading the class outside a running server throws. Test biome-dependent code through seams that do not reference `Biome`, or leave it to runtime.

**Command declaration**: CommandAPI is initialized in `onLoad()`/`onEnable()` and registers commands programmatically (extend cw-commons' `BaseCommand`, then call `.register()` in `onEnable()`). Adding a matching entry under `commands:` in `plugin.yml` makes Bukkit register the same command a second time, which CommandAPI flags at startup with a "Plugin command ... is registered by Bukkit" warning. `permissions:` entries are unaffected and still required.

**Versioning logic** (in `build.gradle.kts`):

- No `-Pver` supplied -> `yyMMdd-HHmm-SNAPSHOT`
- `-Pver=vX.Y.Z-RC-N` -> `X.Y.Z-RC-N-SNAPSHOT`
- `-Pver=vX.Y.Z` -> `X.Y.Z` (stable release)

**CI workflows** (`.github/workflows/`):

- `pr.yml`: builds and tests on Ubuntu + Windows for PRs and merge queue
- `main.yml`: builds, tests, and uploads a snapshot artifact on push to `main`
- `tag.yml`: builds tagged versions and creates a draft GitHub release
- `codeql.yml`: CodeQL analysis for Java and Actions

**Test suites**: `test` contains isolated unit tests and may mock external boundaries.
`integrationTest` exercises the pinned cw-commons dependency directly, including configuration
loading and SQLite persistence. Integration tests use JUnit temporary directories for generated
files. Run this suite separately with `./gradlew integrationTest`.

## Agent instructions

1. Canonical skills live in `.agents/skills/`. The `.claude/skills/` directory is a generated mirror.
2. `CLAUDE.md` is a generated copy of this `AGENTS.md`.
3. Do not edit or create `CLAUDE.md` or files under `.claude/skills/`. Claude hooks configured in `.claude/settings.json` synchronize these mirrors on `SessionStart` and `PostToolUse`.
