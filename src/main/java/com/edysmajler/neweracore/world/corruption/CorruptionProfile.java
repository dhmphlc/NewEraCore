package com.edysmajler.neweracore.world.corruption;

import com.edysmajler.neweracore.config.LevelConfig;

/**
 * The numeric rules for one corruption level.
 *
 * <p>These describe how deep the ashfall lies and how much survived under it — not how many blocks
 * to
 * swap. That distinction is the lesson of three failed attempts: coverage that varies per block
 * reads
 * as vandalism, while coverage that is near-universal and varies in <em>depth</em> reads as
 * weather.
 *
 * <p>Threshold fields are percentiles of their noise field, so lowering one widens the affected
 * area.
 * A value of 1.0 disables a feature and 0.0 makes it universal.
 *
 * @param ashCarpetCoverage share of sheltered ground that takes an ash carpet
 * @param deepAshShare share of covered ground that reaches the paler, deeper ash
 * @param driftChance chance a hollow has drifted deep enough to raise the ground
 * @param scourSlope slope in blocks at which a face is stripped back to rock
 * @param livingGroveThreshold blight percentile below which a stand of trees survives
 * @param snapShare share of dead trees that stand as broken snags rather than full trunks
 * @param collapseShare share of dead trees that come down entirely
 * @param deadBushChance chance a cleared plant leaves a dead bush behind
 * @param waterDryingChance chance shallow water has dried out
 * @param impactZoneThreshold impact percentile above which craters may appear
 * @param cratersPerZone expected craters in a chunk inside an impact zone
 * @param largeCraterShare share of those craters that are large
 */
public record CorruptionProfile(
    double ashCarpetCoverage,
    double deepAshShare,
    double driftChance,
    int scourSlope,
    double livingGroveThreshold,
    double snapShare,
    double collapseShare,
    double deadBushChance,
    double waterDryingChance,
    double impactZoneThreshold,
    double cratersPerZone,
    double largeCraterShare
) {

  /**
   * A profile that changes nothing.
   *
   * <p>Kept as the reference for "before the ashfall". Note the high scour slope: a <em>low</em>
   * value
   * means more of the land strips to rock, so leaving nothing alone requires a slope no terrain
   * reaches.
   */
  public static final CorruptionProfile PRISTINE = new CorruptionProfile(
      0.0, 0.0, 0.0, 8, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0
  );

  /**
   * The lightest ashfall anywhere in the world, used as the calm end of the recovered band.
   *
   * <p>This is where the first principle is enforced structurally rather than by configuration: the
   * least affected chunk in the world still lies under a real ash carpet. Blending the mildest
   * level
   * down towards {@link #PRISTINE} instead would leave a fringe of untouched vanilla ground against
   * corrupted ground, which is the clean/edited boundary that made earlier versions look griefed.
   */
  public static final CorruptionProfile DUSTING = new CorruptionProfile(
      0.45, 0.05, 0.05, 5, 0.6, 0.15, 0.05, 0.25, 0.1, 0.98, 0.05, 0.0
  );

  /**
   * Builds a profile from its configuration section.
   *
   * @param config the level's configuration
   * @return the profile
   */
  public static CorruptionProfile from(LevelConfig config) {
    return new CorruptionProfile(
        config.getAshCarpetCoverage(),
        config.getDeepAshShare(),
        config.getDriftChance(),
        config.getScourSlope(),
        config.getLivingGroveThreshold(),
        config.getSnapShare(),
        config.getCollapseShare(),
        config.getDeadBushChance(),
        config.getWaterDryingChance(),
        config.getImpactZoneThreshold(),
        config.getCratersPerZone(),
        config.getLargeCraterShare()
    );
  }

  /**
   * Interpolates between two profiles.
   *
   * <p>This is what keeps level boundaries from showing as a step: a chunk that only just qualifies
   * as
   * scarred behaves almost like a recovered one, and the ash deepens as it moves into the band.
   *
   * @param calmer the profile at the calm end
   * @param harsher the profile at the severe end
   * @param weight 0 gives the calmer profile, 1 gives the harsher one
   * @return the blended profile
   */
  public static CorruptionProfile blend(
      CorruptionProfile calmer,
      CorruptionProfile harsher,
      double weight
  ) {
    double t = Math.max(0.0, Math.min(1.0, weight));

    return new CorruptionProfile(
        lerp(calmer.ashCarpetCoverage, harsher.ashCarpetCoverage, t),
        lerp(calmer.deepAshShare, harsher.deepAshShare, t),
        lerp(calmer.driftChance, harsher.driftChance, t),
        (int) Math.round(lerp(calmer.scourSlope, harsher.scourSlope, t)),
        lerp(calmer.livingGroveThreshold, harsher.livingGroveThreshold, t),
        lerp(calmer.snapShare, harsher.snapShare, t),
        lerp(calmer.collapseShare, harsher.collapseShare, t),
        lerp(calmer.deadBushChance, harsher.deadBushChance, t),
        lerp(calmer.waterDryingChance, harsher.waterDryingChance, t),
        lerp(calmer.impactZoneThreshold, harsher.impactZoneThreshold, t),
        lerp(calmer.cratersPerZone, harsher.cratersPerZone, t),
        lerp(calmer.largeCraterShare, harsher.largeCraterShare, t)
    );
  }

  private static double lerp(double from, double to, double t) {
    // Return the endpoints exactly: from + (to - from) * 1.0 can drift by an epsilon, and callers
    // compare profiles for equality at the ends of the blend.
    if (t <= 0.0) {
      return from;
    }
    if (t >= 1.0) {
      return to;
    }

    return from + (to - from) * t;
  }
}
