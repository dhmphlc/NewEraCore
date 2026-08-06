package com.edysmajler.neweracore.world.corruption;

import com.edysmajler.neweracore.config.LevelsConfig;
import com.edysmajler.neweracore.config.ThresholdConfig;
import com.edysmajler.neweracore.world.noise.NoiseFields;

/**
 * The corruption level a chunk belongs to, and how deep into that level it sits.
 *
 * <p>The level comes from the broad corruption field sampled at the chunk centre. Because that
 * field
 * changes over hundreds of blocks while a chunk is only sixteen wide, neighbouring chunks nearly
 * always land on the same level, and the map reads as regions.
 *
 * <p>Even the calmest end of the recovered band blends from {@link CorruptionProfile#DUSTING}
 * rather
 * than from nothing, so no chunk anywhere is left as untouched vanilla ground. A boundary between
 * corrupted and pristine land at chunk scale is precisely what reads as an edited world.
 *
 * <p>A discrete level alone would still show its boundaries, so the zone also carries an intensity:
 * how far through the level's band the chunk sits. The effective profile is blended from the level
 * below towards this level by that intensity, which smooths the step out entirely while keeping
 * each
 * level's rules distinct at its core.
 */
public final class CorruptionZone {

  private final CorruptionLevel level;
  private final double intensity;
  private final CorruptionProfile profile;

  private CorruptionZone(CorruptionLevel level, double intensity, CorruptionProfile profile) {
    this.level = level;
    this.intensity = intensity;
    this.profile = profile;
  }

  /**
   * Resolves the zone for a chunk.
   *
   * @param fields the world's noise fields
   * @param thresholds where the level bands begin
   * @param levels the per-level rule sets
   * @param chunkX chunk x coordinate
   * @param chunkZ chunk z coordinate
   * @return the resolved zone
   */
  public static CorruptionZone resolve(
      NoiseFields fields,
      ThresholdConfig thresholds,
      LevelsConfig levels,
      int chunkX,
      int chunkZ
  ) {
    double centerX = chunkX * 16.0 + 8.0;
    double centerZ = chunkZ * 16.0 + 8.0;
    double corruption = fields.corruption().sample(centerX, centerZ);

    double scarredAt = thresholds.getScarredAbove();
    double devastatedAt = Math.max(scarredAt, thresholds.getDevastatedAbove());

    if (corruption >= devastatedAt) {
      double t = smoothstep(band(corruption, devastatedAt, 1.0));
      return new CorruptionZone(
          CorruptionLevel.DEVASTATED,
          t,
          CorruptionProfile.blend(profileOf(levels.getScarred()), profileOf(levels.getDevastated()),
              t)
      );
    }

    if (corruption >= scarredAt) {
      double t = smoothstep(band(corruption, scarredAt, devastatedAt));
      return new CorruptionZone(
          CorruptionLevel.SCARRED,
          t,
          CorruptionProfile.blend(profileOf(levels.getRecovered()), profileOf(levels.getScarred()),
              t)
      );
    }

    double t = smoothstep(band(corruption, 0.0, scarredAt));
    return new CorruptionZone(
        CorruptionLevel.RECOVERED,
        t,
        CorruptionProfile.blend(CorruptionProfile.DUSTING, profileOf(levels.getRecovered()), t)
    );
  }

  /**
   * Returns the level this chunk belongs to.
   *
   * @return the corruption level
   */
  public CorruptionLevel level() {
    return level;
  }

  /**
   * Returns how deep into its level band the chunk sits, from 0 at the band's start to 1 at its
   * end.
   *
   * @return the intensity
   */
  public double intensity() {
    return intensity;
  }

  /**
   * Returns the effective rules, already blended for a smooth transition.
   *
   * @return the profile
   */
  public CorruptionProfile profile() {
    return profile;
  }

  private static CorruptionProfile profileOf(
      com.edysmajler.neweracore.config.LevelConfig config
  ) {
    return CorruptionProfile.from(config);
  }

  private static double band(double value, double from, double to) {
    if (to <= from) {
      return 1.0;
    }
    return Math.max(0.0, Math.min(1.0, (value - from) / (to - from)));
  }

  private static double smoothstep(double t) {
    return t * t * (3.0 - 2.0 * t);
  }
}
