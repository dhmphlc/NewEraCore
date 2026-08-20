package com.edysmajler.neweracore.world;

import com.edysmajler.neweracore.config.TemplateConfig;
import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.biome.BiomeTransformationProcessor;
import com.edysmajler.neweracore.world.biome.BiomeTransformer;
import com.edysmajler.neweracore.world.biome.BiomeTransformerManager;
import com.edysmajler.neweracore.world.biome.DefaultTransformer;
import com.edysmajler.neweracore.world.biome.DesertTransformer;
import com.edysmajler.neweracore.world.biome.ForestTransformer;
import com.edysmajler.neweracore.world.biome.JungleTransformer;
import com.edysmajler.neweracore.world.biome.PlainsTransformer;
import com.edysmajler.neweracore.world.biome.SavannaTransformer;
import com.edysmajler.neweracore.world.biome.SwampTransformer;
import com.edysmajler.neweracore.world.biome.TaigaTransformer;
import com.edysmajler.neweracore.world.feature.HangingPlants;
import com.edysmajler.neweracore.world.structures.FighterJet;
import com.edysmajler.neweracore.world.structures.SchematicStructure;
import com.edysmajler.neweracore.world.structures.StructureDefinition;
import com.edysmajler.neweracore.world.structures.StructureManager;
import com.edysmajler.neweracore.world.structures.StructureMarker;
import com.edysmajler.neweracore.world.structures.StructurePlacer;
import com.edysmajler.neweracore.world.structures.loot.LootTables;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.Plugin;

/**
 * Assembles the world engine, its pipeline, and its biome transformers.
 *
 * <p>This is the single place that decides what runs. A future system is added as a
 * {@link ChunkProcessor} in the pipeline below; a new biome group is added as a
 * {@link BiomeTransformer} in the list; a new scattered structure is registered in
 * {@link #structures}. Nothing else needs to change.
 */
public final class WorldEngineFactory {

  private WorldEngineFactory() {}

  /**
   * Builds the engine for a plugin.
   *
   * @param plugin the owning plugin, used for its namespace and logger
   * @param config the world engine settings
   * @return the assembled engine, ready to register as a listener
   */
  public static WorldEngine create(Plugin plugin, WorldEngineConfig config) {
    List<BiomeTransformer> transformers = List.of(
        new ForestTransformer(),
        new TaigaTransformer(),
        new JungleTransformer(),
        new SwampTransformer(),
        new SavannaTransformer(),
        new PlainsTransformer(),
        new DesertTransformer()
    );

    BiomeTransformerManager manager =
        new BiomeTransformerManager(transformers, new DefaultTransformer());

    StructureManager structures = structures(plugin, config);

    // Order is the design. Structures come after the biome transformation because a crash is a
    // recent event: its crater takes a bite out of the ashen ground rather than being buried by
    // it. HangingPlants stays last, cleaning up after everything that removes a block — including
    // passes nobody has written yet.
    List<ChunkProcessor> pipeline = List.of(
        new BiomeTransformationProcessor(manager),
        new StructurePlacer(structures, new StructureMarker(plugin), plugin.getLogger()),
        new HangingPlants()
    );

    return new WorldEngine(
        config, new ChunkMarker(plugin), pipeline, structures, plugin.getLogger());
  }

  /**
   * Builds the structure registry: the built-in wrecks, then whatever schematic files the server
   * owner dropped in — both read through the per-template config, which can retune a weight or a
   * crash's destruction, or switch a template off, without touching a file.
   *
   * <p>Registration order takes part in the weighted draw, so keep the built-ins first and stable
   * — reordering this list moves every structure in every existing world's ungenerated terrain.
   */
  private static StructureManager structures(Plugin plugin, WorldEngineConfig config) {
    List<StructureDefinition> definitions = new ArrayList<>();

    LootTables lootTables = LootTables.load(config.getStructures(), plugin.getLogger());

    TemplateConfig jet = config.getStructures().templateFor("fighter_jet");
    if (jet.isEnabled()) {
      definitions.add(new FighterJet(
          jet.getWeight(),
          lootTables.resolve(jet.lootTable(LootTables.MILITARY), plugin.getLogger())));
    }

    definitions.addAll(SchematicStructure.loadAll(plugin, config.getStructures(), lootTables));

    StructureManager manager = new StructureManager(definitions);
    // One line at enable: "why is X not spawning" always starts with whether X is registered
    plugin.getLogger().info("Structure scatter registry: "
        + (manager.isEmpty() ? "(empty)" : String.join(", ", manager.ids())));
    return manager;
  }
}
