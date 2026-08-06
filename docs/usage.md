# Adding features

### Adding commands
Commands are registered programmatically through [CommandAPI](https://commandapi.jorel.dev):

1. Create a class extending `BaseCommand` (from [cw-commons](https://github.com/CrimsonWarpedcraft/cw-commons)); build the full `CommandAPICommand` tree in the constructor and pass it to `super(...)` — see `NewEraCoreCommand`
2. Call `.register()` on an instance of it in `NewEraCore.onEnable()`, alongside the existing `new NewEraCoreCommand(config, this).register()`
3. Add its permissions to `plugin.yml`. Do not add the command itself. CommandAPI registers it programmatically
4. Write unit tests for each executor class — mock `CommandSender` and `CommandArguments`, call `run()`, verify `sendRichMessage()`

### Adding subcommands
1. Create a class implementing CommandAPI's `CommandExecutor`; inject any config values via the constructor — see `Info`
2. Add a `.withSubcommand(...)` call in the parent command's constructor
3. Write a unit test for the executor, following `InfoTest`
4. If the subcommand needs a config value, add it to `PluginConfig` and `config.yml`

### Adding world generation behaviour
Anything that reshapes freshly generated terrain belongs in the world engine, not a new listener —
see [docs/world-engine.md](world-engine.md). Write a `ChunkProcessor` and add it in
`WorldEngineFactory`, or a `BiomeTransformer` for biome-specific rules. You inherit the once-per-chunk
guarantee, the snapshot-backed reads, the physics-free writes, and the seeded random source.

### Adding listeners
`GrassToRootedDirtListener` is the reference: implement `Listener`, annotate handlers with
`@EventHandler`, take config values through the constructor, and register it in
`NewEraCore.onEnable()` with
`getServer().getPluginManager().registerEvents(new MyListener(config), this)`. Register behind a
config flag when the behaviour is optional, so a disabled feature costs nothing at runtime.

For world-generation work, filter `ChunkLoadEvent` with `isNewChunk()` — that is what separates
freshly generated terrain from chunks players have already modified. Read blocks from a
`ChunkSnapshot` rather than `Block` objects, and write with `setType(material, false)` so the
change does not trigger physics during chunk load. Scan a bounded y range per column
(`snapshot.getHighestBlockYAt(x, z)` downward, floored at `world.getMinHeight()`) instead of every
block in the chunk.

### Adding new config fields
Config files are loaded with Jackson and validated with Hibernate Validator, via cw-commons'
`Config` interface and `BukkitConfigManagerBuilder`
(`com.crimsonwarpedcraft.cwcommons.config.bukkit`).

Add fields annotated with a Bean Validation constraint and a `@JsonProperty` YAML key to
`PluginConfig` (`config/PluginConfig.java`):

```java
@NotBlank
@JsonProperty("my-message")
private String myMessage = "default";
```

Then add a getter. The config is validated upfront on every startup. If any constraint fails, the plugin logs the offending fields and disables itself cleanly. Read it back through `NewEraCore#getPluginConfig()`.

Add a matching entry to `src/main/resources/config.yml` for every field you add, with a comment if desired:

```yaml
# Description of what the setting controls.
# Supports MiniMessage formatting: https://docs.advntr.dev/minimessage/format.html
my-message: "default"
```

Comments are preserved because `saveDefaultConfig()` writes this file once on first startup and never overwrites it. Schema migrations rewrite the file, but those are rare, and require custom code to handle.

### Adding persistent per-player data
`NewEraCore.onEnable()` opens a SQLite-backed `DataStore` via cw-commons'
`BukkitDataStoreBuilder` and periodically flushes it with `AutoFlushTask`. Reach it through
`NewEraCore#getStore()`. To store JSON-shaped data per player:

1. Create a data class with getters/setters for the fields you want to persist
2. Get a repository from the store: `store.repository("player-data", PlayerData.class, KeySerializers.forUuid())` — one repository backs every field on that class, so adding a field never requires a new repository
3. Wrap it in `new PlayerDataManager<>(repository, this).registerEvents()` to load and save data on join and quit
4. Read and write it with `PlayerDataManager#get`/`#save`, from a `Listener` or a command executor
5. Write unit tests mocking `PlayerDataManager`

Note: `PlayerDataManager` is just a `Player`-keyed convenience wrapper. For data that isn't tied to a specific player, call `DataStore#repository` directly with a different `KeySerializer` (e.g. `KeySerializers.forString()`) to get a standalone `Repository` for that data.
