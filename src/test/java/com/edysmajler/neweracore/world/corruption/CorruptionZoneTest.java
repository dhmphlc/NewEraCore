package com.edysmajler.neweracore.world.corruption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.LevelsConfig;
import com.edysmajler.neweracore.config.NoiseConfig;
import com.edysmajler.neweracore.config.ThresholdConfig;
import com.edysmajler.neweracore.world.noise.NoiseFields;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CorruptionZoneTest {

  private static final int RADIUS = 60;

  private final NoiseFields fields = new NoiseFields(1234L, new NoiseConfig());
  private final ThresholdConfig thresholds = new ThresholdConfig();
  private final LevelsConfig levels = new LevelsConfig();

  @Test
  void neighbouringChunksUsuallyShareTheirLevel() {
    int sampled = 0;
    int agreed = 0;

    for (int x = -RADIUS; x < RADIUS; x++) {
      for (int z = -RADIUS; z < RADIUS; z++) {
        CorruptionLevel here = resolve(x, z).level();
        CorruptionLevel east = resolve(x + 1, z).level();
        sampled++;
        if (here == east) {
          agreed++;
        }
      }
    }

    // The whole point of region-based generation: chunks must not each roll their own level
    double agreement = agreed / (double) sampled;
    assertTrue(agreement > 0.9, "neighbouring chunks disagree too often: " + agreement);
  }

  @Test
  void everyLevelAppearsSomewhere() {
    Map<CorruptionLevel, Integer> counts = new EnumMap<>(CorruptionLevel.class);

    for (int x = -RADIUS; x < RADIUS; x++) {
      for (int z = -RADIUS; z < RADIUS; z++) {
        counts.merge(resolve(x, z).level(), 1, Integer::sum);
      }
    }

    for (CorruptionLevel level : CorruptionLevel.values()) {
      assertTrue(counts.getOrDefault(level, 0) > 0, "no chunks at level " + level);
    }
  }

  @Test
  void recoveredLandRemainsForContrast() {
    int recovered = 0;
    int total = 0;

    for (int x = -RADIUS; x < RADIUS; x++) {
      for (int z = -RADIUS; z < RADIUS; z++) {
        if (resolve(x, z).level() == CorruptionLevel.RECOVERED) {
          recovered++;
        }
        total++;
      }
    }

    // Damage only reads as damage against intact land, but the world is post-catastrophe: enough
    // recovered ground for contrast, not so much that the catastrophe looks like an afterthought.
    double share = recovered / (double) total;
    assertTrue(share > 0.2, "not enough recovered land for contrast: " + share);
    assertTrue(share < 0.55, "too much of the world came through intact: " + share);
  }

  @Test
  void isDeterministicPerChunk() {
    assertEquals(resolve(17, -42).level(), resolve(17, -42).level());
    assertEquals(resolve(17, -42).intensity(), resolve(17, -42).intensity());
  }

  @Test
  void intensityStaysInRange() {
    for (int x = -RADIUS; x < RADIUS; x++) {
      double intensity = resolve(x, 3).intensity();

      assertTrue(intensity >= 0.0 && intensity <= 1.0, "intensity out of range: " + intensity);
    }
  }

  @Test
  void recoveredChunksAreGentlerThanDevastatedOnes() {
    CorruptionProfile recovered = deepestProfileAt(CorruptionLevel.RECOVERED);
    CorruptionProfile devastated = deepestProfileAt(CorruptionLevel.DEVASTATED);

    assertTrue(
        devastated.ashCarpetCoverage() > recovered.ashCarpetCoverage(),
        "devastated land must lie under deeper ash than recovered land"
    );
    assertTrue(
        devastated.livingGroveThreshold() < recovered.livingGroveThreshold(),
        "devastated land must keep less living forest"
    );
  }

  @Test
  void levelIsAtLeastOrdersSeverity() {
    assertTrue(CorruptionLevel.DEVASTATED.isAtLeast(CorruptionLevel.SCARRED));
    assertTrue(CorruptionLevel.SCARRED.isAtLeast(CorruptionLevel.SCARRED));
    assertFalse(CorruptionLevel.RECOVERED.isAtLeast(CorruptionLevel.SCARRED));
  }

  /**
   * Returns the profile of the chunk that sits deepest inside the wanted level.
   */
  private CorruptionProfile deepestProfileAt(CorruptionLevel wanted) {
    CorruptionProfile deepest = null;
    double best = -1.0;

    for (int x = -RADIUS; x < RADIUS; x++) {
      for (int z = -RADIUS; z < RADIUS; z++) {
        CorruptionZone zone = resolve(x, z);
        if (zone.level() == wanted && zone.intensity() > best) {
          best = zone.intensity();
          deepest = zone.profile();
        }
      }
    }

    if (deepest == null) {
      throw new IllegalStateException("no chunk found at level " + wanted);
    }

    return deepest;
  }

  private CorruptionZone resolve(int chunkX, int chunkZ) {
    return CorruptionZone.resolve(fields, thresholds, levels, chunkX, chunkZ);
  }
}
