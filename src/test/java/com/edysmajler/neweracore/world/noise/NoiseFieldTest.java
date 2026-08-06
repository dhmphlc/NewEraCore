package com.edysmajler.neweracore.world.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoiseFieldTest {

  private static final int SAMPLES = 4000;

  @Test
  void staysBetweenZeroAndOne() {
    NoiseField field = new NoiseField(11L, 1L, 96, 3);

    for (int i = 0; i < SAMPLES; i++) {
      double value = field.sample(i * 7 - 9000, i * 13 - 4000);

      assertTrue(value >= 0.0 && value <= 1.0, "out of range: " + value);
    }
  }

  @Test
  void isDeterministic() {
    NoiseField first = new NoiseField(11L, 1L, 96, 3);
    NoiseField second = new NoiseField(11L, 1L, 96, 3);

    assertEquals(first.sample(512, -256), second.sample(512, -256));
  }

  @Test
  void saltsSeparateFields() {
    NoiseField patch = new NoiseField(11L, 2L, 96, 3);
    NoiseField blight = new NoiseField(11L, 3L, 96, 3);

    // Fields over one world must be independent, or blighted trees would track dead ground exactly
    double correlated = 0;
    for (int i = 0; i < SAMPLES; i++) {
      if (Math.abs(patch.sample(i * 3, i * 5) - blight.sample(i * 3, i * 5)) < 0.02) {
        correlated++;
      }
    }

    assertTrue(correlated / SAMPLES < 0.2, "fields track each other too closely");
  }

  @Test
  void isContinuousAcrossChunkBorders() {
    NoiseField field = new NoiseField(11L, 1L, 96, 3);

    // Chunk-border seams would be visible in terrain, so neighbouring columns must agree closely
    for (int x = -320; x < 320; x += 16) {
      double inside = field.sample(x + 15, 0);
      double across = field.sample(x + 16, 0);

      assertTrue(Math.abs(inside - across) < 0.1, "seam at x=" + x);
    }
  }

  @Test
  void calibratedOutputIsEvenlySpread() {
    NoiseField field = new NoiseField(11L, 1L, 96, 3);
    int[] deciles = new int[10];

    for (int i = 0; i < SAMPLES; i++) {
      double value = field.sample(i * 11 - 20000, i * 17 - 9000);
      deciles[Math.min(9, (int) (value * 10))]++;
    }

    // Thresholds are written as percentiles, so each decile must hold roughly a tenth of the world.
    // Uncalibrated octave sums cluster near 0.5 and leave the top deciles empty, which silently
    // disables every feature gated above about 0.8.
    for (int i = 0; i < deciles.length; i++) {
      double share = deciles[i] / (double) SAMPLES;
      assertTrue(share > 0.04, "decile " + i + " holds only " + share);
      assertTrue(share < 0.18, "decile " + i + " holds " + share);
    }
  }

  @Test
  void rawOutputClustersInTheMiddle() {
    NoiseField field = new NoiseField(11L, 1L, 96, 3);
    int extremes = 0;

    for (int i = 0; i < SAMPLES; i++) {
      double raw = field.rawSample(i * 11 - 20000, i * 17 - 9000);
      if (raw > 0.8 || raw < 0.2) {
        extremes++;
      }
    }

    // Documents why calibration exists: the raw field almost never reaches the extremes
    assertTrue(extremes / (double) SAMPLES < 0.15, "raw field is flatter than expected");
  }

  @Test
  void largerScaleChangesMoreSlowly() {
    NoiseField fine = new NoiseField(11L, 1L, 16, 1);
    NoiseField broad = new NoiseField(11L, 1L, 512, 1);

    assertTrue(averageStep(broad) < averageStep(fine), "scale must control feature size");
  }

  private static double averageStep(NoiseField field) {
    double total = 0.0;

    for (int i = 1; i < SAMPLES; i++) {
      total += Math.abs(field.rawSample(i, 0) - field.rawSample(i - 1, 0));
    }

    return total / (SAMPLES - 1);
  }
}
