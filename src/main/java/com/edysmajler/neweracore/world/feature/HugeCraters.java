package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.config.HugeCraterConfig;
import com.edysmajler.neweracore.config.OreConfig;
import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ash.AshPalette;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Carves the rare impacts that span many chunks.
 *
 * <p>These are the events the rest of the world is reacting to, so they are allowed to be enormous
 * and
 * are correspondingly rare — a few hundred blocks of walking between them at most.
 *
 * <p>The bowl is stamped as a <em>depth profile relative to each column's own ground</em> rather
 * than
 * as a sphere around an absolute point. That is what lets a crater cross chunk borders seamlessly:
 * a
 * column only needs its distance from the centre to know how deep to cut, so two chunks carving
 * their
 * halves independently produce one continuous bowl. It also means the crater follows the terrain it
 * landed in instead of hovering at a fixed height.
 */
public final class HugeCraters {

  /** How far past the radius debris is thrown, as a multiple of the radius. */
  private static final double EJECTA_REACH = 1.35;

  /** Where the raised rim begins, as a fraction of the radius. */
  private static final double RIM_START = 0.82;

  private HugeCraters() {}

  /**
   * Carves any huge crater slices that fall inside this chunk.
   *
   * @param context the chunk being transformed
   * @param palette the biome's materials
   * @param sites the sites reaching this chunk
   */
  public static void apply(ChunkContext context, AshPalette palette, List<CraterSite> sites) {
    if (sites.isEmpty()) {
      return;
    }

    HugeCraterConfig config = context.getConfig().getHugeCraters();
    OreConfig ores = context.getConfig().getOres();

    for (CraterSite site : sites) {
      carveSlice(context, palette, ores, config, site);
    }
  }

  private static void carveSlice(
      ChunkContext context,
      AshPalette palette,
      OreConfig ores,
      HugeCraterConfig config,
      CraterSite site
  ) {
    for (int x = 0; x < ChunkContext.CHUNK_SIZE; x++) {
      for (int z = 0; z < ChunkContext.CHUNK_SIZE; z++) {
        double distance = site.distanceTo(context.blockX(x), context.blockZ(z));

        if (distance <= site.radius()) {
          carveColumn(context, palette, ores, config, site, distance, x, z);
        } else if (distance <= site.radius() * EJECTA_REACH) {
          throwEjecta(context, palette, ores, site, distance, x, z);
        }
      }
    }
  }

  private static void carveColumn(
      ChunkContext context,
      AshPalette palette,
      OreConfig ores,
      HugeCraterConfig config,
      CraterSite site,
      double distance,
      int x,
      int z
  ) {
    int groundY = context.groundY(x, z);
    int depth = depthAt(config, site, distance);

    if (depth <= 0) {
      raiseRim(context, palette, site, distance, x, z, groundY);
      return;
    }

    for (int i = 0; i < depth; i++) {
      int y = groundY - i;
      if (isCarvable(context.typeAt(x, y, z))) {
        context.set(x, y, z, Material.AIR);
      }
    }

    floorAt(context, palette, ores, x, groundY - depth, z);
  }

  /**
   * Returns how deep to cut a column: deepest at the centre, tapering to nothing at the rim.
   */
  private static int depthAt(HugeCraterConfig config, CraterSite site, double distance) {
    double normalized = distance / site.radius();
    double bowl = Math.sqrt(Math.max(0.0, 1.0 - normalized * normalized));
    return (int) Math.round(bowl * site.radius() * config.getDepthFactor());
  }

  /**
   * Lays the crater floor, with seams of ore torn out of the rock below.
   */
  private static void floorAt(
      ChunkContext context,
      AshPalette palette,
      OreConfig ores,
      int x,
      int y,
      int z
  ) {
    Material ore = ExposedOres.rollFor(context, ores, y, true);
    context.set(x, y, z, ore != null ? ore : palette.debrisAt(context.between(0, 8)));
  }

  /**
   * Piles the ground up slightly just outside the bowl, so the crater has a lip.
   */
  private static void raiseRim(
      ChunkContext context,
      AshPalette palette,
      CraterSite site,
      double distance,
      int x,
      int z,
      int groundY
  ) {
    if (distance < site.radius() * RIM_START) {
      return;
    }

    if (!context.chance(0.4) || context.typeAt(x, groundY + 1, z) != Material.AIR) {
      return;
    }

    context.set(x, groundY + 1, z, palette.debrisAt(context.between(0, 8)));
  }

  /**
   * Scatters what came out of the hole across the land around it, thinning with distance.
   */
  private static void throwEjecta(
      ChunkContext context,
      AshPalette palette,
      OreConfig ores,
      CraterSite site,
      double distance,
      int x,
      int z
  ) {
    double past = (distance - site.radius()) / (site.radius() * (EJECTA_REACH - 1.0));
    if (!context.chance((1.0 - past) * (1.0 - past))) {
      return;
    }

    int groundY = context.groundY(x, z);
    if (!context.typeAt(x, groundY, z).isSolid()) {
      return;
    }

    Material ore = ExposedOres.rollFor(context, ores, groundY, true);
    context.set(x, groundY, z, ore != null ? ore : palette.debrisAt(context.between(0, 8)));
  }

  private static boolean isCarvable(Material material) {
    if (material.isAir()) {
      return false;
    }

    return material != Material.WATER
        && material != Material.LAVA
        && material != Material.BEDROCK
        && !Tag.ICE.isTagged(material);
  }
}
