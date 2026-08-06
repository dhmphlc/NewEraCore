package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.config.OreConfig;
import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ash.AshPalette;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import org.bukkit.Material;

/**
 * Carves impact craters inside impact zones.
 *
 * <p>Craters only appear where the impact field clears the level's threshold, which turns them into
 * clusters: a run of neighbouring chunks inside one zone all get hit, and the land between zones is
 * left whole. Rolling per chunk instead would sprinkle lone craters evenly over the map, which
 * reads
 * as random damage rather than a bombardment.
 *
 * <p>The bowl is carved from a sphere centred just under the surface. Blocks well inside the radius
 * are removed outright, while blocks near the rim are removed with a probability that tapers to
 * zero,
 * so the outline stays ragged. Surviving rim blocks become debris from the biome's palette.
 */
public final class Craters {

  /** Width of the fuzzy band at the crater edge, in blocks. */
  private static final double EDGE_BAND = 1.2;

  private static final int SMALL_RADIUS_MIN = 2;
  private static final int SMALL_RADIUS_MAX = 4;
  private static final int LARGE_RADIUS_MIN = 5;
  private static final int LARGE_RADIUS_MAX = 7;

  /** Chance a rim block that survives becomes debris. */
  private static final double SCATTER_CHANCE = 0.35;

  /** How far past the rim ejecta is thrown, as a multiple of the radius. */
  private static final double EJECTA_REACH = 1.7;

  private Craters() {}

  /**
   * Carves this chunk's craters, if it lies inside an impact zone.
   *
   * @param context the chunk being transformed
   * @param palette the biome's materials
   */
  public static void apply(ChunkContext context, AshPalette palette) {
    CorruptionProfile profile = context.profile();

    if (context.impact() < profile.impactZoneThreshold()) {
      return;
    }

    int craters = context.count(profile.cratersPerZone());

    for (int i = 0; i < craters; i++) {
      boolean large = context.chance(profile.largeCraterShare());
      int radius = large
          ? context.between(LARGE_RADIUS_MIN, LARGE_RADIUS_MAX)
          : context.between(SMALL_RADIUS_MIN, SMALL_RADIUS_MAX);

      carve(context, palette, radius);
    }
  }

  private static void carve(ChunkContext context, AshPalette palette, int radius) {
    int limit = ChunkContext.CHUNK_SIZE - 1;
    int clamped = Math.min(radius, ChunkContext.CHUNK_SIZE / 2);
    int centerX = context.between(clamped, limit - clamped);
    int centerZ = context.between(clamped, limit - clamped);
    // Sink the centre slightly so the bowl bites into the ground instead of hovering over it
    int centerY = context.groundY(centerX, centerZ) - clamped / 2;

    for (int dx = -clamped; dx <= clamped; dx++) {
      for (int dz = -clamped; dz <= clamped; dz++) {
        carveColumn(context, palette, centerX, centerY, centerZ, clamped, dx, dz);
      }
    }

    throwEjecta(context, palette, centerX, centerZ, clamped);
  }

  /**
   * Sprays debris outside the rim, thinning with distance.
   *
   * <p>Without an apron a crater ends abruptly at its lip, which looks cut rather than blasted. The
   * material that came out of the hole has to land somewhere.
   */
  private static void throwEjecta(
      ChunkContext context,
      AshPalette palette,
      int centerX,
      int centerZ,
      int radius
  ) {
    int reach = (int) Math.ceil(radius * EJECTA_REACH);

    for (int dx = -reach; dx <= reach; dx++) {
      for (int dz = -reach; dz <= reach; dz++) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= radius || distance > reach) {
          continue;
        }

        int x = centerX + dx;
        int z = centerZ + dz;
        if (!context.inChunk(x, z)) {
          continue;
        }

        double falloff = 1.0 - (distance - radius) / Math.max(1.0, reach - radius);
        if (!context.chance(falloff * falloff)) {
          continue;
        }

        // Ice is a solid block, so a frozen lake passes for ground unless fluid columns are named
        // outright. Without this the apron paves a ring of debris across the ice sheet.
        if (context.isFluidColumn(x, z)) {
          continue;
        }

        int surfaceY = context.groundY(x, z);
        if (context.typeAt(x, surfaceY, z).isSolid()) {
          context.set(x, surfaceY, z, debrisOrOre(context, palette, surfaceY, false));
        }
      }
    }
  }

  private static void carveColumn(
      ChunkContext context,
      AshPalette palette,
      int centerX,
      int centerY,
      int centerZ,
      int radius,
      int dx,
      int dz
  ) {
    int x = centerX + dx;
    int z = centerZ + dz;

    if (!context.inChunk(x, z)) {
      return;
    }

    // The bowl only just clears the surface, so a tree standing in it would keep everything above
    // the sphere and hang there. Whatever this column reached has to come away with it.
    int spread = (int) Math.sqrt(Math.max(0, radius * radius - dx * dx - dz * dz));
    int columnTop = centerY + spread;
    if (columnTop >= context.groundY(x, z)) {
      Blast.clearAbove(context, palette, x, columnTop, z);
    }

    for (int dy = -radius; dy <= radius; dy++) {
      double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (distance > radius) {
        continue;
      }

      int y = centerY + dy;
      if (distance <= radius - EDGE_BAND) {
        excavate(context, x, y, z);
      } else {
        blendEdge(context, palette, x, y, z, distance, radius);
      }
    }
  }

  private static void excavate(ChunkContext context, int x, int y, int z) {
    if (isCarvable(context.typeAt(x, y, z))) {
      context.clear(x, y, z);
    }
  }

  private static void blendEdge(
      ChunkContext context,
      AshPalette palette,
      int x,
      int y,
      int z,
      double distance,
      int radius
  ) {
    if (!isCarvable(context.typeAt(x, y, z))) {
      return;
    }

    // Removal probability tapers to zero at the rim, so the outline stays ragged
    if (context.chance((radius - distance) / EDGE_BAND)) {
      context.clear(x, y, z);
      return;
    }

    if (context.chance(SCATTER_CHANCE)) {
      context.set(x, y, z, debrisOrOre(context, palette, y, false));
    }
  }

  /**
   * Returns debris, or a seam of ore torn out of the shallow rock.
   *
   * @param context the chunk being transformed
   * @param palette the biome's materials
   * @param y absolute height of the block
   * @param huge whether this is a huge crater
   * @return the material to place
   */
  private static Material debrisOrOre(
      ChunkContext context,
      AshPalette palette,
      int y,
      boolean huge
  ) {
    OreConfig ores = context.getConfig().getOres();
    Material ore = ExposedOres.rollFor(context, ores, y, huge);
    return ore != null ? ore : palette.debrisAt(context.between(0, 8));
  }

  private static boolean isCarvable(Material material) {
    // Leave fluids alone: draining an ocean or lava lake into a crater looks broken and would
    // trigger the fluid updates this engine deliberately avoids
    return !material.isAir() && !ChunkContext.isFluid(material);
  }
}
