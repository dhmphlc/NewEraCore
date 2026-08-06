package com.edysmajler.neweracore.world.biome;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ash.AshPalette;
import com.edysmajler.neweracore.world.feature.TreeScan;
import com.edysmajler.neweracore.world.terrain.TerrainProbe;
import java.util.Set;
import org.bukkit.block.Biome;

/**
 * Biome-specific corruption rules.
 *
 * <p>Work splits in two because some effects are per-column and some need the whole chunk.
 * {@link #transformColumn} runs for every column whose surface biome this transformer claims, so a
 * chunk straddling a border is treated correctly on each side. {@link #transformChunk} runs once,
 * for
 * the transformer that claimed most of the chunk's columns, and is where tree and crater work
 * belongs.
 *
 * <p>Implement this directly only for a biome that needs an unusual pipeline. Everything else
 * should
 * extend {@link AbstractBiomeTransformer}, which supplies the standard behaviour and leaves a new
 * biome group needing little more than its biome set and its palette.
 */
public interface BiomeTransformer {

  /**
   * Returns a short name used in log messages.
   *
   * @return the transformer name
   */
  String name();

  /**
   * Returns the biomes this transformer handles.
   *
   * @return the claimed biomes, empty for a fallback transformer
   */
  Set<Biome> biomes();

  /**
   * Returns the materials this biome turns into under the ashfall.
   *
   * @return the palette
   */
  AshPalette palette();

  /**
   * Corrupts one column of the chunk.
   *
   * @param context the chunk being transformed
   * @param probe the chunk's terrain facts
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   */
  void transformColumn(ChunkContext context, TerrainProbe probe, int x, int z);

  /**
   * Applies chunk-wide effects. Called only for the chunk's dominant transformer.
   *
   * @param context the chunk being transformed
   * @param probe the chunk's terrain facts
   * @param scan the chunk's collected trunks, logs, and leaves
   */
  void transformChunk(ChunkContext context, TerrainProbe probe, TreeScan scan);
}
