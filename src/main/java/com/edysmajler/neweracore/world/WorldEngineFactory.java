package com.edysmajler.neweracore.world;

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
import java.util.List;
import org.bukkit.plugin.Plugin;

/**
 * Assembles the world engine, its pipeline, and its biome transformers.
 *
 * <p>This is the single place that decides what runs. A future system — radiation, restoration,
 * ruins,
 * loot, custom structures — is added as a {@link ChunkProcessor} in the pipeline below; a new biome
 * group is added as a {@link BiomeTransformer} in the list below. Nothing else needs to change.
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

    List<ChunkProcessor> pipeline = List.of(new BiomeTransformationProcessor(manager));

    return new WorldEngine(config, new ChunkMarker(plugin), pipeline, plugin.getLogger());
  }
}
