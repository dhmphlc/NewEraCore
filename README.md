# NewEraCore
Core plugin for the New Era PaperMC server.

[![](https://github.com/edysmajler/NewEraCore/actions/workflows/main.yml/badge.svg)](https://github.com/edysmajler/NewEraCore/actions/workflows/main.yml)

## Features
NewEraCore currently provides the plugin foundation that server features build on:

* **World corruption engine** — buries freshly generated terrain under an ashen nuclear winter, exactly once per chunk. See [the world engine docs](docs/world-engine.md)
  * **Ash falls on everything.** Coverage is universal and only its depth varies, the way snow works in a winter biome — no clean/altered boundary anywhere
  * **Whole shapes, not block edits.** A tree is either untouched or dead: no leaves at all, charred basalt trunk, bare branch stubs, sometimes snapped or collapsed
  * **Terrain-coupled.** Ash drifts deep into hollows, lies flat on open ground, and blows off steep faces down to bare rock
  * Three levels — `RECOVERED`, `SCARRED`, `DEVASTATED` — from OpenSimplex noise, so neighbouring chunks share a level and transitions blend
  * Dry streambeds, crater clusters inside impact zones, dead undergrowth that leaves litter rather than swept ground
  * Biome identity preserved: one shared ash carpet over per-biome palettes, so a buried taiga still differs from a buried swamp
* **Simulated history** — three world-scale layers (war, ashfall, restoration) give every region a story: front line, ruined towns, ashen waste, green refuge, dust bowl. Every system reads the same `RegionProfile`, so a place cannot contradict itself. Rare landmark sites (silos, dams, airports, hospitals) are placed deterministically for future structure generators
* **Infrastructure before buildings** — highways, railways, and power lines connect the landmarks over a Gabriel graph, with bridges where they cross water. What runs between two places follows from what they are: rail to a works, a power line between dam and mast, a highway for everything else. Laid down before the ashfall, so ash settles over the tarmac and craters break it. Every later system can ask `nearestRoute(x, z)`, which is the point
* `/neweracore` (alias `/nec`), all gated behind `neweracore.admin`:
  * `info` — the running plugin version
  * `here` — what happened where you are standing: story, the three history layers, the numbers the passes actually ran on, the nearest landmark, and whether this chunk predates the plugin
  * `craters` — the huge impact craters within 3000 blocks, with coordinates, bearings, and sizes
  * `here` also reports what route runs past and how far away it is
* Config loading and validation via [cw-commons](https://github.com/CrimsonWarpedcraft/cw-commons)' `BukkitConfigManagerBuilder`, backed by [Jackson](https://github.com/fasterxml/jackson) and [Hibernate Validator](https://hibernate.org/validator/)
* Persistent SQLite-backed storage via cw-commons' `BukkitDataStoreBuilder`, with periodic flushing through `AutoFlushTask`
* [CommandAPI](https://commandapi.jorel.dev) wired up for programmatic command registration with subcommands, tab completion, and permissions
* Shaded and relocated dependencies, so the JAR drops in without classloader conflicts

### Build tooling 🏗
* Gradle build with [Shadow](https://gradleup.com/shadow/) packaging
* [Checkstyle](https://checkstyle.org/) Google standard style check
* [SpotBugs](https://spotbugs.github.io/) code analysis
* [JUnit 5](https://junit.org/) unit tests with [Mockito](https://site.mockito.org/), plus integration tests against real cw-commons

### Automation 🎬
* GitHub Actions builds, tests, and release drafting
* CodeQL analysis for Java and Actions
* Dependabot updates for Actions workflows and Gradle dependencies
* Probot: Stale marks issues stale after 30 days
* Bug report and feature request issue templates

## Documentation
- [Adding features](docs/usage.md) — commands, subcommands, config fields, and persistent per-player data
- [Releases & versioning](docs/releases.md) — PaperMC compatibility, version format, and how to cut a release
- [Agent instructions & skills](docs/skills.md) — agent guidance, Claude Code support, and available skills

## Building locally
Thanks to [Gradle](https://gradle.org/), building locally is easy no matter what platform you're on. Simply run the following command:

```text
./gradlew build
```

This build step will also run all checks and tests, making sure your code is clean.

Run `./gradlew test` for isolated unit tests or `./gradlew integrationTest` for tests that use
real cw-commons configuration and storage implementations. Integration-test files are created in
JUnit temporary directories and removed automatically.

JARs can be found in `build/libs/`.

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md).
