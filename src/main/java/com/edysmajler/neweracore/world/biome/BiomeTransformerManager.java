package com.edysmajler.neweracore.world.biome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.block.Biome;

/**
 * Selects the transformer responsible for a biome.
 *
 * <p>Each transformer declares the biomes it claims, and the manager indexes them once at startup.
 * Anything unclaimed — including biomes added by future game versions — falls back to a default
 * transformer, so no chunk is ever left unhandled.
 */
public class BiomeTransformerManager {

  private final Map<Biome, BiomeTransformer> byBiome = new HashMap<>();
  private final BiomeTransformer fallback;

  /**
   * Indexes the given transformers by biome.
   *
   * @param transformers the biome-specific transformers
   * @param fallback the transformer used for biomes nobody claims
   */
  public BiomeTransformerManager(List<BiomeTransformer> transformers, BiomeTransformer fallback) {
    this.fallback = fallback;

    for (BiomeTransformer transformer : transformers) {
      for (Biome biome : transformer.biomes()) {
        byBiome.put(biome, transformer);
      }
    }
  }

  /**
   * Returns the transformer for a biome.
   *
   * @param biome the biome to look up
   * @return the claiming transformer, or the fallback
   */
  public BiomeTransformer forBiome(Biome biome) {
    return byBiome.getOrDefault(biome, fallback);
  }
}
