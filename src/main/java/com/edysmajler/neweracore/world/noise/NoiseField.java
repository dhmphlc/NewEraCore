package com.edysmajler.neweracore.world.noise;

import java.util.Arrays;

/**
 * An octaved {@link OpenSimplexNoise} sampler whose output is spread evenly over 0 to 1.
 *
 * <p>Each field has its own scale and its own seed salt, so two fields over the same world are
 * independent: where trees are blighted has nothing to do with where the ground cracks, and neither
 * lines up with the impact zones.
 *
 * <p><strong>Why the calibration matters.</strong> Summing octaves produces a bell-shaped
 * distribution clustered around 0.5, not a flat one — values above roughly 0.8 almost never occur.
 * Comparing such a field against a threshold like 0.9 therefore fires approximately never, which
 * silently disables whatever feature depends on it. To make thresholds mean what they look like,
 * the
 * raw field is calibrated once at construction against a grid of samples and every read is
 * converted
 * to its percentile. A threshold of 0.6 then genuinely selects the top 40% of the world.
 *
 * <p>The mapping is monotonic, so it preserves the continuity and determinism of the underlying
 * field: neighbouring blocks still get neighbouring values, and the same coordinates always give
 * the
 * same answer.
 */
public final class NoiseField {

  private static final double LACUNARITY = 2.0;
  private static final double GAIN = 0.5;

  /** Grid edge used to calibrate the distribution; 96 squared is 9216 samples. */
  private static final int CALIBRATION_EDGE = 96;

  private final OpenSimplexNoise[] octaves;
  private final double frequency;
  private final double amplitudeTotal;
  private final double[] quantiles;

  /**
   * Creates a field.
   *
   * @param worldSeed the world seed
   * @param salt distinguishes this field from other fields over the same world
   * @param scaleInBlocks approximate width in blocks of one feature
   * @param octaveCount how many octaves to sum; more adds fine detail at linear cost
   */
  public NoiseField(long worldSeed, long salt, double scaleInBlocks, int octaveCount) {
    int count = Math.max(1, octaveCount);
    this.octaves = new OpenSimplexNoise[count];
    for (int i = 0; i < count; i++) {
      octaves[i] = new OpenSimplexNoise(worldSeed ^ (salt * 0x9E3779B97F4A7C15L + i));
    }

    this.frequency = 1.0 / Math.max(1.0, scaleInBlocks);

    double total = 0.0;
    double amplitude = 1.0;
    for (int i = 0; i < count; i++) {
      total += amplitude;
      amplitude *= GAIN;
    }
    this.amplitudeTotal = total;

    this.quantiles = calibrate(scaleInBlocks);
  }

  /**
   * Samples the field at a world position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the value's percentile within this field, between 0 and 1
   */
  public double sample(double blockX, double blockZ) {
    return percentileOf(raw(blockX, blockZ));
  }

  /**
   * Samples the uncalibrated field.
   *
   * <p>Exposed for tests that need to see the distribution the calibration corrects.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the raw octave sum mapped to 0 to 1
   */
  public double rawSample(double blockX, double blockZ) {
    return raw(blockX, blockZ);
  }

  private double raw(double blockX, double blockZ) {
    double sum = 0.0;
    double amplitude = 1.0;
    double currentFrequency = frequency;

    for (OpenSimplexNoise octave : octaves) {
      sum += amplitude * octave.sample(blockX * currentFrequency, blockZ * currentFrequency);
      amplitude *= GAIN;
      currentFrequency *= LACUNARITY;
    }

    double normalized = 0.5 + 0.5 * (sum / amplitudeTotal);
    return Math.max(0.0, Math.min(1.0, normalized));
  }

  /**
   * Samples a spread-out grid and keeps the sorted values as the field's empirical distribution.
   */
  private double[] calibrate(double scaleInBlocks) {
    double stride = Math.max(1.0, scaleInBlocks / 2.0);
    double[] samples = new double[CALIBRATION_EDGE * CALIBRATION_EDGE];
    int index = 0;

    for (int i = 0; i < CALIBRATION_EDGE; i++) {
      for (int j = 0; j < CALIBRATION_EDGE; j++) {
        // Offset the grid off the origin so it does not sit on lattice points
        double x = (i - CALIBRATION_EDGE / 2.0) * stride + 0.37;
        double z = (j - CALIBRATION_EDGE / 2.0) * stride + 0.11;
        samples[index++] = raw(x, z);
      }
    }

    Arrays.sort(samples);
    return samples;
  }

  /**
   * Converts a raw value to its position within the calibrated distribution.
   */
  private double percentileOf(double value) {
    int low = 0;
    int high = quantiles.length - 1;

    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (quantiles[mid] < value) {
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }

    return low / (double) (quantiles.length - 1);
  }
}
