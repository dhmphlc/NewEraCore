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

`WorldEngineFactory` wires the `ChunkProcessor` pipeline and the transformer list; new systems go there. `AbstractBiomeTransformer` holds the shared pass order — dry beds, undergrowth, ash mantle, then trees and craters — so a biome group needs only its biome set, its `AshPalette`, and the odd flag. The shared ash carpet across all palettes is what makes it one event; the materials underneath keep biome identity.

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
