package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.config.OreConfig;
import com.edysmajler.neweracore.world.ChunkContext;
import org.bukkit.Material;

/**
 * Places ore thrown up by an impact.
 *
 * <p>An impact deep enough to leave a crater tears through whatever was under the soil, so the
 * floor
 * and walls should show seams that were never meant to be at the surface. Small craters scratch the
 * shallow layers and expose coal and copper; a huge one reaches far enough down to leave iron,
 * gold,
 * and the occasional stripe of something better lying in the open.
 *
 * <p>Deepslate variants are used below y=0, so the ore matches the rock it sits in instead of
 * looking
 * pasted on.
 */
public final class ExposedOres {

  private ExposedOres() {}

  /**
   * Returns the ore to expose at a position, or null to leave the debris as it is.
   *
   * @param context the chunk being transformed
   * @param config the ore chances
   * @param y absolute height of the block
   * @param huge whether this is a huge crater, which reaches deeper material
   * @return the ore material, or null
   */
  public static Material rollFor(ChunkContext context, OreConfig config, int y, boolean huge) {
    double chance = huge ? config.getHugeCraterChance() : config.getSmallCraterChance();
    if (!context.chance(chance)) {
      return null;
    }

    boolean deep = y < 0;

    if (huge && context.chance(config.getPreciousShare())) {
      return precious(context, deep);
    }

    if (context.chance(config.getIronShare())) {
      return deep ? Material.DEEPSLATE_IRON_ORE : Material.IRON_ORE;
    }

    return context.nextBoolean()
        ? (deep ? Material.DEEPSLATE_COAL_ORE : Material.COAL_ORE)
        : (deep ? Material.DEEPSLATE_COPPER_ORE : Material.COPPER_ORE);
  }

  /**
   * Returns something worth the walk out to a crater.
   */
  private static Material precious(ChunkContext context, boolean deep) {
    return switch (context.between(0, 5)) {
      case 0 -> deep ? Material.DEEPSLATE_GOLD_ORE : Material.GOLD_ORE;
      case 1 -> deep ? Material.DEEPSLATE_REDSTONE_ORE : Material.REDSTONE_ORE;
      case 2 -> deep ? Material.DEEPSLATE_LAPIS_ORE : Material.LAPIS_ORE;
      case 3 -> Material.RAW_IRON_BLOCK;
      case 4 -> Material.RAW_COPPER_BLOCK;
      default -> deep ? Material.DEEPSLATE_DIAMOND_ORE : Material.DIAMOND_ORE;
    };
  }
}
