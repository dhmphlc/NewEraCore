package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.config.HugeCraterConfig;
import com.edysmajler.neweracore.world.history.HistoryEngine;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the huge impact sites near a chunk.
 *
 * <p>A crater wider than a chunk cannot be rolled per chunk: every chunk it covers has to agree on
 * exactly where its centre is and how wide it is, or the bowl comes out as a set of mismatched
 * fragments. So sites live on a coarse grid in world coordinates and are derived by hashing the
 * cell, which makes them a pure function of the world seed. Each chunk then carves only the slice
 * that falls inside it, and neighbouring chunks line up perfectly without ever loading each other.
 *
 * <p>Sites are only kept where the war map says the fighting reached <em>and</em> the generator
 * puts land — a bowl at sea carves nothing, because fluid columns are skipped, so it is a rare
 * feature spent on a walk to look at open water. Rejecting those costs perhaps a third of the sites
 * and makes the rest count.
 *
 * <p>The war test is what ties the biggest hole in the ground to everything around it: the same
 * layer that put the crater here also thinned the groves, flattened the deadfall, and scattered the
 * smaller impacts across the same region. Keying it to the corruption level instead — as this did
 * before there was a history to consult — made the largest event in the world an unrelated
 * coincidence sitting in land that happened to be dark.
 */
public final class CraterSites {

  private static final long SITE_SALT = 0x51ED270BL;

  /** How far past its own radius a site has to stay dry, as a multiple of the radius. */
  private static final double DRY_REACH = 1.5;

  /** How many points around the rim are tested for water. */
  private static final int DRY_SAMPLES = 8;

  private CraterSites() {}

  /**
   * Returns the huge crater sites that reach a chunk.
   *
   * @param config the huge crater settings
   * @param history the world's simulated history
   * @param land what the world generator puts at a position, land or open water
   * @param worldSeed the world seed
   * @param chunkX chunk x coordinate
   * @param chunkZ chunk z coordinate
   * @param reach how far past the radius to consider a chunk affected
   * @return the sites affecting this chunk, usually empty
   */
  public static List<CraterSite> near(
      HugeCraterConfig config,
      HistoryEngine history,
      LandLookup land,
      long worldSeed,
      int chunkX,
      int chunkZ,
      double reach
  ) {
    int spacing = config.getSpacing();
    int originX = chunkX * 16;
    int originZ = chunkZ * 16;
    int cellX = Math.floorDiv(originX, spacing);
    int cellZ = Math.floorDiv(originZ, spacing);

    List<CraterSite> sites = new ArrayList<>();

    // The widest crater plus its apron can only ever reach one cell over
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        CraterSite site = siteIn(config, worldSeed, cellX + dx, cellZ + dz);
        if (site == null || !site.reaches(originX, originZ, reach)) {
          continue;
        }

        if (isWorthCarving(history, land, site)) {
          sites.add(site);
        }
      }
    }

    return sites;
  }

  /**
   * Returns every huge crater within a radius of a position, nearest first.
   *
   * <p>{@link #near} answers what a chunk has to carve, which is a different question: it only
   * returns sites whose bowl actually reaches that chunk, so a crater eight hundred blocks away is
   * invisible to it. This answers "where are they", for a player who wants to go and find one.
   *
   * <p>Pure arithmetic over the seed and the noise maps, so it needs nothing loaded and can be
   * asked about anywhere.
   *
   * @param config the huge crater settings
   * @param history the world's simulated history
   * @param land what the world generator puts at a position, land or open water
   * @param worldSeed the world seed
   * @param blockX absolute block x to search around
   * @param blockZ absolute block z to search around
   * @param blockRadius how far to look, in blocks
   * @return the sites found, ordered by distance
   */
  public static List<CraterSite> around(
      HugeCraterConfig config,
      HistoryEngine history,
      LandLookup land,
      long worldSeed,
      int blockX,
      int blockZ,
      int blockRadius
  ) {
    int spacing = config.getSpacing();
    int cells = (int) Math.ceil(blockRadius / (double) spacing);
    int cellX = Math.floorDiv(blockX, spacing);
    int cellZ = Math.floorDiv(blockZ, spacing);

    List<CraterSite> found = new ArrayList<>();

    for (int dx = -cells; dx <= cells; dx++) {
      for (int dz = -cells; dz <= cells; dz++) {
        CraterSite site = siteIn(config, worldSeed, cellX + dx, cellZ + dz);

        if (site != null
            && site.distanceTo(blockX, blockZ) <= blockRadius
            && isWorthCarving(history, land, site)) {
          found.add(site);
        }
      }
    }

    found.sort(Comparator.comparingDouble(site -> site.distanceTo(blockX, blockZ)));
    return found;
  }

  /**
   * Returns the crater in one grid cell, or null when that cell holds none.
   */
  private static CraterSite siteIn(
      HugeCraterConfig config,
      long worldSeed,
      int cellX,
      int cellZ
  ) {
    long hash = mix(worldSeed ^ SITE_SALT, cellX, cellZ);

    if (unitFrom(hash) >= config.getChance()) {
      return null;
    }

    int spacing = config.getSpacing();
    long positionHash = mix(hash, 0x9E37L, 0x85EBL);
    long radiusHash = mix(hash, 0xC2B2L, 0x27D4L);

    int centerX = cellX * spacing + (int) (unitFrom(positionHash) * spacing);
    int centerZ = cellZ * spacing + (int) (unitFrom(mix(positionHash, 1, 1)) * spacing);
    int span = Math.max(0, config.getRadiusMax() - config.getRadiusMin());
    int radius = config.getRadiusMin() + (int) (unitFrom(radiusHash) * (span + 1));

    return new CraterSite(centerX, centerZ, radius);
  }

  /**
   * Returns whether a site is worth having: struck by the war, and on dry land.
   *
   * <p>Reading the history is pure arithmetic over the noise maps, so it asks about a location far
   * outside the current chunk without loading anything — which is the whole reason a crater wider
   * than a chunk can exist at all. {@link LandLookup} keeps the same promise for the land test.
   */
  private static boolean isWorthCarving(HistoryEngine history, LandLookup land, CraterSite site) {
    return history.at(site.centerX(), site.centerZ()).story().isWarTorn()
        && isWellInland(land, site);
  }

  /**
   * Returns whether a site is far enough from water to stay a hole rather than become a lake.
   *
   * <p>Testing the centre alone was not enough. A bowl fifteen blocks deep whose <em>rim</em>
   * reaches a shoreline is worse than one at sea: the sea does not stay politely outside it.
   * Nothing flows while the chunk is being written, because every write here is physics-free, but
   * the first block update near that bank lets the water in and the crater fills to sea level.
   *
   * <p>So the rim is sampled too, and at half again the radius, which leaves a bank thick enough
   * that water cannot reach across it. Eight points around a circle is coarse, but the things it
   * has to catch — an ocean, a river — are far larger than the gaps between the samples.
   */
  private static boolean isWellInland(LandLookup land, CraterSite site) {
    if (!land.isLand(site.centerX(), site.centerZ())) {
      return false;
    }

    int reach = (int) Math.round(site.radius() * DRY_REACH);

    for (int i = 0; i < DRY_SAMPLES; i++) {
      double angle = i * 2.0 * Math.PI / DRY_SAMPLES;
      int x = site.centerX() + (int) Math.round(Math.cos(angle) * reach);
      int z = site.centerZ() + (int) Math.round(Math.sin(angle) * reach);

      if (!land.isLand(x, z)) {
        return false;
      }
    }

    return true;
  }

  private static long mix(long seed, long x, long z) {
    long hash = seed;
    hash ^= x * 0x9E3779B97F4A7C15L;
    hash ^= z * 0xC2B2AE3D27D4EB4FL;
    hash ^= hash >>> 33;
    hash *= 0xFF51AFD7ED558CCDL;
    hash ^= hash >>> 33;
    hash *= 0xC4CEB9FE1A85EC53L;
    hash ^= hash >>> 33;
    return hash;
  }

  private static double unitFrom(long hash) {
    return (hash >>> 11) * 0x1.0p-53;
  }
}
