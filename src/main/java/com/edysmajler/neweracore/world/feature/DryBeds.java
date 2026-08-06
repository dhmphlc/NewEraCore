package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ash.AshPalette;
import com.edysmajler.neweracore.world.terrain.TerrainProbe;
import org.bukkit.Material;

/**
 * Drains shallow water and leaves a dry bed.
 *
 * <p>A pristine blue pond in the middle of an ash field destroys the illusion faster than anything
 * else on this list. Only shallow water goes — a stream, a puddle, the edge of a pool — and what is
 * left is cracked bed material rather than a hole, so it reads as a watercourse that dried up.
 *
 * <p>Lakes and oceans stay. Draining them would leave enormous unnatural pits, and a dead sea under
 * ash is a stronger image than a canyon where the sea used to be.
 */
public final class DryBeds {

  /** Deepest water the pass will touch. */
  private static final int MAX_DEPTH = 3;

  private DryBeds() {}

  /**
   * Dries one column.
   *
   * @param context the chunk being transformed
   * @param probe the chunk's terrain facts
   * @param palette the biome's materials
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   */
  public static void applyToColumn(
      ChunkContext context,
      TerrainProbe probe,
      AshPalette palette,
      int x,
      int z
  ) {
    int surfaceY = context.groundY(x, z);
    if (context.typeAt(x, surfaceY, z) != Material.WATER) {
      return;
    }

    if (!context.chance(context.profile().waterDryingChance())) {
      return;
    }

    int depth = depthAt(context, x, surfaceY, z);
    if (depth < 1 || depth > MAX_DEPTH) {
      return;
    }

    for (int i = 0; i < depth; i++) {
      int y = surfaceY - i;
      boolean bed = i == depth - 1;
      context.set(x, y, z, bed ? bedFor(context, palette, x, z) : Material.AIR);
    }

    // A drained bed beside standing water keeps a muddy fringe rather than a clean edge
    if (probe.isNearWater(x, z)) {
      context.set(x, surfaceY - depth + 1, z, palette.dryBed());
    }
  }

  private static int depthAt(ChunkContext context, int x, int surfaceY, int z) {
    int depth = 0;

    while (depth <= MAX_DEPTH && context.typeAt(x, surfaceY - depth, z) == Material.WATER) {
      depth++;
    }

    return depth;
  }

  private static Material bedFor(ChunkContext context, AshPalette palette, int x, int z) {
    return context.detailAt(x, z) < 0.6 ? palette.dryBed() : palette.grit();
  }
}
