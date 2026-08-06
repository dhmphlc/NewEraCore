package com.edysmajler.neweracore.world.biome;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ash.AshMantle;
import com.edysmajler.neweracore.world.feature.Craters;
import com.edysmajler.neweracore.world.feature.DeadTrees;
import com.edysmajler.neweracore.world.feature.DeadUndergrowth;
import com.edysmajler.neweracore.world.feature.DryBeds;
import com.edysmajler.neweracore.world.feature.HugeCraters;
import com.edysmajler.neweracore.world.feature.TreeScan;
import com.edysmajler.neweracore.world.terrain.TerrainProbe;

/**
 * The standard corruption pipeline, shared by every biome group.
 *
 * <p>Adding a biome group means extending this class, declaring its biomes and palette, and
 * toggling
 * the one or two features that do not apply. No feature call order is duplicated, so a new
 * transformer
 * cannot drift from the others.
 *
 * <p>The order matters. Water dries before ash settles, so a drained bed takes the mantle like any
 * other ground. Undergrowth is cleared before the mantle so ash lies on top of what is left. Trees
 * are
 * last, because a felled trunk should come to rest on the finished surface.
 */
public abstract class AbstractBiomeTransformer implements BiomeTransformer {

  @Override
  public void transformColumn(ChunkContext context, TerrainProbe probe, int x, int z) {
    DryBeds.applyToColumn(context, probe, palette(), x, z);
    DeadUndergrowth.applyToColumn(context, palette(), x, z);
    AshMantle.applyToColumn(context, probe, palette(), x, z);
  }

  @Override
  public void transformChunk(ChunkContext context, TerrainProbe probe, TreeScan scan) {
    if (hasTrees()) {
      DeadTrees.apply(context, scan, palette());
    }

    Craters.apply(context, palette());
    HugeCraters.apply(context, palette(), context.hugeCraterSites());
  }

  /**
   * Returns whether this biome grows trees worth killing.
   *
   * @return true when the biome has trees
   */
  protected boolean hasTrees() {
    return true;
  }
}
