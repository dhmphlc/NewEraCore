package com.edysmajler.neweracore.world.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenSimplexNoiseTest {

  private static final int SAMPLES = 5000;

  @Test
  void staysWithinRange() {
    OpenSimplexNoise noise = new OpenSimplexNoise(7L);

    for (int i = 0; i < SAMPLES; i++) {
      double value = noise.sample(i * 0.37, i * -0.91);

      assertTrue(value >= -1.05 && value <= 1.05, "out of range: " + value);
    }
  }

  @Test
  void isDeterministicPerSeed() {
    OpenSimplexNoise first = new OpenSimplexNoise(99L);
    OpenSimplexNoise second = new OpenSimplexNoise(99L);

    assertEquals(first.sample(12.5, -8.25), second.sample(12.5, -8.25));
  }

  @Test
  void differentSeedsGiveDifferentFields() {
    assertNotEquals(
        new OpenSimplexNoise(1L).sample(4.5, 4.5),
        new OpenSimplexNoise(2L).sample(4.5, 4.5)
    );
  }

  @Test
  void isContinuous() {
    OpenSimplexNoise noise = new OpenSimplexNoise(4242L);
    double step = 0.01;

    // Neighbouring samples must not jump, or patches would show hard edges mid-terrain
    for (int i = 0; i < SAMPLES; i++) {
      double x = i * step;
      double delta = Math.abs(noise.sample(x, 3.0) - noise.sample(x + step, 3.0));

      assertTrue(delta < 0.2, "discontinuity at x=" + x + ": " + delta);
    }
  }

  @Test
  void variesAcrossSpace() {
    OpenSimplexNoise noise = new OpenSimplexNoise(31L);
    double lowest = 1.0;
    double highest = -1.0;

    for (int i = 0; i < SAMPLES; i++) {
      double value = noise.sample(i * 0.13, i * 0.29);
      lowest = Math.min(lowest, value);
      highest = Math.max(highest, value);
    }

    assertTrue(highest - lowest > 1.0, "field range too narrow: " + (highest - lowest));
  }

  @Test
  void hasNoAxisAlignedBias() {
    OpenSimplexNoise noise = new OpenSimplexNoise(5L);
    double alongAxis = 0.0;
    double alongDiagonal = 0.0;

    // A value-noise grid shows up as different variance along the axes than across them; simplex
    // gradients should not, which is why this implementation replaced the earlier lattice noise.
    for (int i = 1; i < SAMPLES; i++) {
      alongAxis += Math.abs(noise.sample(i * 0.05, 0.0) - noise.sample((i - 1) * 0.05, 0.0));
      alongDiagonal += Math.abs(
          noise.sample(i * 0.05, i * 0.05) - noise.sample((i - 1) * 0.05, (i - 1) * 0.05)
      );
    }

    double ratio = alongDiagonal / Math.max(1.0e-9, alongAxis);
    assertTrue(ratio > 0.5 && ratio < 3.0, "directional bias detected, ratio " + ratio);
  }
}
