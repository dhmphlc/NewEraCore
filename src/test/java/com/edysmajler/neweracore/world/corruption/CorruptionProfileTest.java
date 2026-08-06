package com.edysmajler.neweracore.world.corruption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CorruptionProfileTest {

  @Test
  void pristineProfileChangesNothing() {
    CorruptionProfile pristine = CorruptionProfile.PRISTINE;

    assertEquals(0.0, pristine.ashCarpetCoverage());
    assertEquals(1.0, pristine.livingGroveThreshold(), "every grove must survive when pristine");
    // A 1.0 percentile is unreachable, so no craters can appear
    assertEquals(1.0, pristine.impactZoneThreshold());
    assertEquals(0.0, pristine.collapseShare());
    // A low scour slope means MORE scouring, so leaving the land alone needs a high one
    assertTrue(pristine.scourSlope() >= 8);
  }

  @Test
  void theLightestAshfallStillCoversGround() {
    // The first principle, enforced in code rather than configuration: nowhere is left vanilla
    assertTrue(CorruptionProfile.DUSTING.ashCarpetCoverage() > 0.4);
    assertTrue(CorruptionProfile.DUSTING.livingGroveThreshold() < 1.0);
  }

  @Test
  void blendAtZeroGivesTheCalmerProfile() {
    assertEquals(
        CorruptionProfile.PRISTINE,
        CorruptionProfile.blend(CorruptionProfile.PRISTINE, harsh(), 0.0)
    );
  }

  @Test
  void blendAtOneGivesTheHarsherProfile() {
    assertEquals(harsh(), CorruptionProfile.blend(CorruptionProfile.PRISTINE, harsh(), 1.0));
  }

  @Test
  void blendHalfwaySitsBetween() {
    CorruptionProfile blended = CorruptionProfile.blend(CorruptionProfile.PRISTINE, harsh(), 0.5);

    assertEquals(0.5, blended.ashCarpetCoverage(), 1.0e-9);
    assertEquals(1.0, blended.cratersPerZone(), 1.0e-9);
  }

  @Test
  void blendClampsOutOfRangeWeights() {
    assertEquals(harsh(), CorruptionProfile.blend(CorruptionProfile.PRISTINE, harsh(), 4.0));
    assertEquals(
        CorruptionProfile.PRISTINE,
        CorruptionProfile.blend(CorruptionProfile.PRISTINE, harsh(), -2.0)
    );
  }

  @Test
  void blendingTowardsHarsherDeepensTheAsh() {
    CorruptionProfile calm = CorruptionProfile.blend(CorruptionProfile.PRISTINE, harsh(), 0.1);
    CorruptionProfile severe = CorruptionProfile.blend(CorruptionProfile.PRISTINE, harsh(), 0.9);

    assertTrue(severe.ashCarpetCoverage() > calm.ashCarpetCoverage());
    // Lower threshold means fewer groves survive
    assertTrue(severe.livingGroveThreshold() < calm.livingGroveThreshold());
    // Lower scour slope means more of the land strips back to rock
    assertTrue(severe.scourSlope() <= calm.scourSlope());
  }

  private static CorruptionProfile harsh() {
    return new CorruptionProfile(1.0, 0.7, 0.8, 2, 0.0, 0.8, 0.5, 0.45, 0.95, 0.4, 2.0, 0.3);
  }
}
