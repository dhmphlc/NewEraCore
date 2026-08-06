package com.edysmajler.neweracore.world.corruption;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.LevelsConfig;
import com.edysmajler.neweracore.config.NoiseConfig;
import com.edysmajler.neweracore.config.ThresholdConfig;
import com.edysmajler.neweracore.world.noise.NoiseFields;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Asserts the observable *amount* of corruption, not that the wiring exists.
 *
 * <p>These tests exist because of two real failures. First, thresholds were written as though the
 * noise fields were uniform when they cluster around the middle, so craters and burned forests were
 * configured and then never generated once. Second, ground changes were gated behind a patch
 * threshold, so most of the world stayed vanilla and the affected columns looked picked out one by
 * one. Both shipped looking correct. Only coverage measurements catch either.
 */
class CorruptionStatisticsTest {

  private static final int RADIUS = 40;
  private static final int COLUMN_STRIDE = 4;

  private final NoiseFields fields = new NoiseFields(20260806L, new NoiseConfig());
  private final ThresholdConfig thresholds = new ThresholdConfig();
  private final LevelsConfig levels = new LevelsConfig();

  @Test
  void everyLevelCoversMeaningfulGround() {
    Map<CorruptionLevel, Integer> counts = levelCounts();
    int total = counts.values().stream().mapToInt(Integer::intValue).sum();

    for (CorruptionLevel level : CorruptionLevel.values()) {
      double share = counts.getOrDefault(level, 0) / (double) total;

      assertTrue(share > 0.15, level + " covers only " + percent(share) + " of the world");
      assertTrue(share < 0.6, level + " covers " + percent(share) + ", crowding out the others");
    }
  }

  @Test
  void ashCoversTheWholeWorld() {
    // The core of the redesign: no level leaves the ground vanilla, so there is no clean/edited
    // boundary anywhere. Ash depth varies; ash presence does not.
    for (CorruptionLevel level : CorruptionLevel.values()) {
      double coverage = averageCarpetCoverage(level);

      assertTrue(coverage > 0.4, level + " only carpets " + percent(coverage) + " of its ground");
    }
  }

  @Test
  void ashDeepensTowardsTheWorstLand() {
    assertTrue(averageCarpetCoverage(CorruptionLevel.DEVASTATED)
        > averageCarpetCoverage(CorruptionLevel.RECOVERED));
    assertTrue(averageDeepAshShare(CorruptionLevel.DEVASTATED)
        > averageDeepAshShare(CorruptionLevel.SCARRED));
  }

  @Test
  void devastatedForestIsEntirelyDead() {
    double living = livingGroveShare(CorruptionLevel.DEVASTATED);

    // Averaged over the band including its fringe, where the profile still blends towards scarred
    assertTrue(living < 0.05, "living forest in devastated land: " + percent(living));
  }

  @Test
  void scarredForestKeepsOnlyPockets() {
    double living = livingGroveShare(CorruptionLevel.SCARRED);

    assertTrue(living > 0.005, "scarred land has no surviving pockets at all");
    assertTrue(living < 0.25, "scarred land keeps " + percent(living) + " of its forest");
  }

  @Test
  void recoveredForestLargelySurvives() {
    double living = livingGroveShare(CorruptionLevel.RECOVERED);

    assertTrue(living > 0.25, "recovered land keeps only " + percent(living) + " of its forest");
  }

  @Test
  void cratersActuallyGenerate() {
    double devastated = impactZoneShare(CorruptionLevel.DEVASTATED);
    double scarred = impactZoneShare(CorruptionLevel.SCARRED);

    assertTrue(devastated > 0.35, "devastated chunks in an impact zone: " + percent(devastated));
    assertTrue(scarred > 0.05, "scarred chunks in an impact zone: " + percent(scarred));
  }

  @Test
  void cratersStayRareInRecoveredLand() {
    double recovered = impactZoneShare(CorruptionLevel.RECOVERED);

    assertTrue(recovered < 0.15, "craters leaked into recovered land: " + percent(recovered));
  }

  private Map<CorruptionLevel, Integer> levelCounts() {
    Map<CorruptionLevel, Integer> counts = new EnumMap<>(CorruptionLevel.class);

    for (int x = -RADIUS; x < RADIUS; x++) {
      for (int z = -RADIUS; z < RADIUS; z++) {
        counts.merge(zone(x, z).level(), 1, Integer::sum);
      }
    }

    return counts;
  }

  private double averageCarpetCoverage(CorruptionLevel wanted) {
    return averageOverLevel(wanted, profile -> profile.ashCarpetCoverage());
  }

  private double averageDeepAshShare(CorruptionLevel wanted) {
    return averageOverLevel(wanted, profile -> profile.deepAshShare());
  }

  /**
   * Averages one profile value across every chunk at a level, including its blended fringe.
   */
  private double averageOverLevel(CorruptionLevel wanted, ProfileValue value) {
    double total = 0.0;
    int sampled = 0;

    for (int x = -RADIUS; x < RADIUS; x++) {
      for (int z = -RADIUS; z < RADIUS; z++) {
        CorruptionZone zone = zone(x, z);
        if (zone.level() != wanted) {
          continue;
        }

        total += value.of(zone.profile());
        sampled++;
      }
    }

    return sampled == 0 ? 0.0 : total / sampled;
  }

  /**
   * Returns the share of columns whose stand of trees survives, across chunks at one level.
   */
  private double livingGroveShare(CorruptionLevel wanted) {
    int living = 0;
    int sampled = 0;

    for (int chunkX = -RADIUS; chunkX < RADIUS; chunkX++) {
      for (int chunkZ = -RADIUS; chunkZ < RADIUS; chunkZ++) {
        CorruptionZone zone = zone(chunkX, chunkZ);
        if (zone.level() != wanted) {
          continue;
        }

        double threshold = zone.profile().livingGroveThreshold();
        for (int x = 0; x < 16; x += COLUMN_STRIDE) {
          for (int z = 0; z < 16; z += COLUMN_STRIDE) {
            double blight = fields.blight().sample(chunkX * 16.0 + x, chunkZ * 16.0 + z);
            sampled++;
            if (blight < threshold) {
              living++;
            }
          }
        }
      }
    }

    return sampled == 0 ? 0.0 : living / (double) sampled;
  }

  private double impactZoneShare(CorruptionLevel wanted) {
    int inZone = 0;
    int sampled = 0;

    for (int chunkX = -RADIUS; chunkX < RADIUS; chunkX++) {
      for (int chunkZ = -RADIUS; chunkZ < RADIUS; chunkZ++) {
        CorruptionZone zone = zone(chunkX, chunkZ);
        if (zone.level() != wanted) {
          continue;
        }

        double impact = fields.impact().sample(chunkX * 16.0 + 8.0, chunkZ * 16.0 + 8.0);
        sampled++;
        if (impact >= zone.profile().impactZoneThreshold()) {
          inZone++;
        }
      }
    }

    return sampled == 0 ? 0.0 : inZone / (double) sampled;
  }

  private CorruptionZone zone(int chunkX, int chunkZ) {
    return CorruptionZone.resolve(fields, thresholds, levels, chunkX, chunkZ);
  }

  private static String percent(double share) {
    return Math.round(share * 100.0) + "%";
  }

  /**
   * Reads one numeric field out of a profile.
   */
  private interface ProfileValue {

    double of(CorruptionProfile profile);
  }
}
