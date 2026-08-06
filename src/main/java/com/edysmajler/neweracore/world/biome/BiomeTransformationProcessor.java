package com.edysmajler.neweracore.world.biome;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ChunkProcessor;
import com.edysmajler.neweracore.world.feature.TreeScan;
import com.edysmajler.neweracore.world.terrain.TerrainProbe;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pipeline stage that dispatches each column to the transformer for its biome.
 *
 * <p>Columns are handled individually, so a chunk on a biome border is not forced into one
 * treatment
 * and the boundary between two corrupted biomes stays where the biome boundary is.
 *
 * <p>Chunk-wide effects then run once, on whichever transformer claimed the most columns. The tree
 * scan is taken here and passed in, so the chunk is swept once no matter how many tree effects the
 * dominant transformer runs.
 */
public class BiomeTransformationProcessor implements ChunkProcessor {

  private final BiomeTransformerManager manager;

  /**
   * Creates the stage.
   *
   * @param manager the biome to transformer index
   */
  public BiomeTransformationProcessor(BiomeTransformerManager manager) {
    this.manager = manager;
  }

  @Override
  public String name() {
    return "biome-corruption";
  }

  @Override
  public void process(ChunkContext context) {
    // The tree scan is taken before anything is written, so it sees the chunk as it generated
    TreeScan scan = TreeScan.of(context);
    TerrainProbe probe = new TerrainProbe(context);

    Map<BiomeTransformer, Integer> columnCounts = transformColumns(context, probe);

    dominant(columnCounts).ifPresent(transformer ->
        transformer.transformChunk(context, probe, scan));
  }

  private Map<BiomeTransformer, Integer> transformColumns(
      ChunkContext context,
      TerrainProbe probe
  ) {
    Map<BiomeTransformer, Integer> columnCounts = new HashMap<>();

    for (int x = 0; x < ChunkContext.CHUNK_SIZE; x++) {
      for (int z = 0; z < ChunkContext.CHUNK_SIZE; z++) {
        BiomeTransformer transformer = manager.forBiome(context.biomeAt(x, z));

        transformer.transformColumn(context, probe, x, z);
        columnCounts.merge(transformer, 1, Integer::sum);
      }
    }

    return columnCounts;
  }

  private static Optional<BiomeTransformer> dominant(
      Map<BiomeTransformer, Integer> columnCounts
  ) {
    return columnCounts.entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey);
  }
}
