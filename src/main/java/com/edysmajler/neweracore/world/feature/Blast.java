package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.Vegetation;
import com.edysmajler.neweracore.world.ash.AshPalette;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Takes away what was standing on the ground an impact just removed.
 *
 * <p>Both crater passes cut <em>downwards</em>: the huge ones stamp a depth from each column's
 * ground level, and the ordinary ones carve a sphere that barely clears the surface. Neither ever
 * looked at what was above. So a crater opened under a forest and left the forest hanging over the
 * hole — trunks, canopy, the ash carpet, all floating in mid-air, because physics is disabled and
 * nothing falls on its own. It is the most visible artefact this engine can produce, right in the
 * middle of its most dramatic feature.
 *
 * <p>An impact takes the trees with it. That is not a detail, it is the entire reading of a crater.
 *
 * <p>What counts as "standing on the ground" is everything that is neither ground nor fluid:
 * trunks, leaves, carpets, snow, plants, and the solid plants like bamboo. Terrain is left alone,
 * so an overhang above a crater stays an overhang.
 */
public final class Blast {

  private Blast() {}

  /**
   * Clears everything left standing above a height in one column.
   *
   * @param context the chunk being transformed
   * @param palette the biome's materials, for the charred wood this engine puts up itself
   * @param x chunk-relative x, 0-15
   * @param topY the highest block the impact removed; everything above it goes
   * @param z chunk-relative z, 0-15
   */
  public static void clearAbove(
      ChunkContext context,
      AshPalette palette,
      int x,
      int topY,
      int z
  ) {
    int ceiling = context.surfaceY(x, z) + Vegetation.REACH;

    for (int y = ceiling; y > topY; y--) {
      if (isStandingOnGround(context.typeAt(x, y, z), palette)) {
        context.clear(x, y, z);
      }
    }
  }

  /**
   * Returns whether a material is something that was standing on the ground rather than being it.
   */
  private static boolean isStandingOnGround(Material material, AshPalette palette) {
    if (material.isAir() || ChunkContext.isFluid(material)) {
      return false;
    }

    // The palette's own wood has to be named. Trees are killed before craters are carved, so by the
    // time an impact arrives there are no logs left to recognise — only the charred basalt this
    // engine stood up in their place, which sailed straight through a check for LOGS and left
    // stumps hanging over the hole.
    if (material == palette.charredWood() || material == palette.fallenWood()) {
      return true;
    }

    // Anything that is not solid was growing or settling on the surface: plants, carpets, snow
    return !material.isSolid()
        || Vegetation.isStanding(material)
        || Tag.LOGS.isTagged(material)
        || Tag.LEAVES.isTagged(material);
  }
}
