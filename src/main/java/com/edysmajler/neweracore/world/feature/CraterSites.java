package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.config.HugeCraterConfig;
import com.edysmajler.neweracore.config.LevelsConfig;
import com.edysmajler.neweracore.config.ThresholdConfig;
import com.edysmajler.neweracore.world.corruption.CorruptionLevel;
import com.edysmajler.neweracore.world.corruption.CorruptionZone;
import com.edysmajler.neweracore.world.noise.NoiseFields;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the huge impact sites near a chunk.
 *
 * <p>A crater wider than a chunk cannot be rolled per chunk: every chunk it covers has to agree on
 * exactly where its centre is and how wide it is, or the bowl comes out as a set of mismatched
 * fragments. So sites live on a coarse grid in world coordinates and are derived by hashing the
 * cell,
 * which makes them a pure function of the world seed. Each chunk then carves only the slice that
 * falls
 * inside it, and neighbouring chunks line up perfectly without ever loading each other.
 *
 * <p>Sites are only kept where the region is already devastated, which puts the largest impacts
 * inside
 * the worst land — the crater reads as the reason that region is the way it is.
 */
public final class CraterSites {

  private static final long SITE_SALT = 0x51ED270BL;

  private CraterSites() {}

  /**
   * Returns the huge crater sites that reach a chunk.
   *
   * @param config the huge crater settings
   * @param thresholds where the corruption bands begin
   * @param levels the per-level rules
   * @param fields the world's noise fields
   * @param worldSeed the world seed
   * @param chunkX chunk x coordinate
   * @param chunkZ chunk z coordinate
   * @param reach how far past the radius to consider a chunk affected
   * @return the sites affecting this chunk, usually empty
   */
  public static List<CraterSite> near(
      HugeCraterConfig config,
      ThresholdConfig thresholds,
      LevelsConfig levels,
      NoiseFields fields,
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

        if (isDevastatedAt(thresholds, levels, fields, site)) {
          sites.add(site);
        }
      }
    }

    return sites;
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
   * Returns whether the region around a site is devastated enough to hold it.
   *
   * <p>Resolving the zone is pure arithmetic over the noise fields, so this asks about a location
   * far
   * outside the current chunk without loading anything.
   */
  private static boolean isDevastatedAt(
      ThresholdConfig thresholds,
      LevelsConfig levels,
      NoiseFields fields,
      CraterSite site
  ) {
    CorruptionZone zone = CorruptionZone.resolve(
        fields,
        thresholds,
        levels,
        Math.floorDiv(site.centerX(), 16),
        Math.floorDiv(site.centerZ(), 16)
    );

    return zone.level() == CorruptionLevel.DEVASTATED;
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
