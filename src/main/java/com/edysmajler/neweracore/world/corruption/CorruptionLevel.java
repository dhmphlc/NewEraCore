package com.edysmajler.neweracore.world.corruption;

/**
 * How badly a chunk was hit.
 *
 * <p>The level is decided by broad-scale noise, so neighbouring chunks almost always share one and
 * the world is made of regions rather than a per-chunk lottery. It selects which rules apply at
 * all;
 * the numeric strength within a level comes from {@link CorruptionProfile}.
 */
public enum CorruptionLevel {

  /** Came through largely intact: vanilla terrain with small dead patches and healthy trees. */
  RECOVERED,

  /** Visibly damaged: dead ground, dying trees, deadfall, the occasional burned stand. */
  SCARRED,

  /** At the epicentre: burned forest, wide dead ground, rubble, clusters of craters. */
  DEVASTATED;

  /**
   * Returns whether this level is at least as severe as another.
   *
   * <p>Used by features that only exist past a threshold, such as rubble fields.
   *
   * @param other the level to compare against
   * @return true when this level is the same or worse
   */
  public boolean isAtLeast(CorruptionLevel other) {
    return ordinal() >= other.ordinal();
  }
}
