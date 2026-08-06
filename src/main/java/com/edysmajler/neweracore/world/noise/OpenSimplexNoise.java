package com.edysmajler.neweracore.world.noise;

/**
 * Two-dimensional OpenSimplex noise.
 *
 * <p>OpenSimplex is used rather than a hand-rolled value noise because it has no axis-aligned
 * artifacts: value noise interpolated on a square lattice leaves faint grid lines and blobs that
 * line up with the axes, which shows up in terrain as suspiciously rectangular patches. Simplex
 * gradients give organic, direction-free shapes, which is exactly what "this region was damaged"
 * needs to look like.
 *
 * <p>Output is continuous and roughly in the range -1 to 1. Sampling is allocation free and depends
 * only on the coordinates and the seed, so results are reproducible and identical across chunk
 * borders.
 *
 * <p>This is the classic OpenSimplex algorithm by Kurt Spencer, released into the public domain.
 */
public final class OpenSimplexNoise {

  private static final double STRETCH = -0.211324865405187;
  private static final double SQUISH = 0.366025403784439;
  private static final double NORMALIZER = 47.0;

  private static final long LCG_MULTIPLIER = 6364136223846793005L;
  private static final long LCG_INCREMENT = 1442695040888963407L;

  /**
   * Gradient vectors for the 2D case, as x and y pairs.
   */
  private static final byte[] GRADIENTS = {
      5, 2, 2, 5,
      -5, 2, -2, 5,
      5, -2, 2, -5,
      -5, -2, -2, -5
  };

  private final short[] permutation = new short[256];

  /**
   * Creates a noise instance for a seed.
   *
   * @param seed the seed; the same seed always produces the same field
   */
  public OpenSimplexNoise(long seed) {
    short[] source = new short[256];
    for (short i = 0; i < 256; i++) {
      source[i] = i;
    }

    long state = seed;
    state = state * LCG_MULTIPLIER + LCG_INCREMENT;
    state = state * LCG_MULTIPLIER + LCG_INCREMENT;
    state = state * LCG_MULTIPLIER + LCG_INCREMENT;

    for (int i = 255; i >= 0; i--) {
      state = state * LCG_MULTIPLIER + LCG_INCREMENT;
      int index = (int) ((state + 31) % (i + 1));
      if (index < 0) {
        index += i + 1;
      }
      permutation[i] = source[index];
      source[index] = source[i];
    }
  }

  /**
   * Samples the field.
   *
   * @param x x coordinate
   * @param y y coordinate
   * @return a value roughly between -1 and 1
   */
  public double sample(double x, double y) {
    // Place the point on the skewed simplex lattice
    double stretchOffset = (x + y) * STRETCH;
    double xs = x + stretchOffset;
    double ys = y + stretchOffset;

    int xsb = floor(xs);
    int ysb = floor(ys);

    double squishOffset = (xsb + ysb) * SQUISH;
    double dx0 = x - (xsb + squishOffset);
    double dy0 = y - (ysb + squishOffset);

    double xins = xs - xsb;
    double yins = ys - ysb;
    double inSum = xins + yins;

    double value = contribution(xsb + 1, ysb, dx0 - 1 - SQUISH, dy0 - SQUISH)
        + contribution(xsb, ysb + 1, dx0 - SQUISH, dy0 - 1 - SQUISH);

    Corner corner = inSum <= 1
        ? lowerCorner(xsb, ysb, xins, yins, inSum, dx0, dy0)
        : upperCorner(xsb, ysb, xins, yins, inSum, dx0, dy0);

    value += contribution(corner.baseX(), corner.baseY(), corner.baseDx(), corner.baseDy());
    value += contribution(corner.extraX(), corner.extraY(), corner.extraDx(), corner.extraDy());

    return value / NORMALIZER;
  }

  /**
   * Resolves the base and extra vertices for a point inside the lower simplex.
   */
  private static Corner lowerCorner(
      int xsb,
      int ysb,
      double xins,
      double yins,
      double inSum,
      double dx0,
      double dy0
  ) {
    double zins = 1 - inSum;

    if (zins > xins || zins > yins) {
      if (xins > yins) {
        return new Corner(xsb, ysb, dx0, dy0, xsb + 1, ysb - 1, dx0 - 1, dy0 + 1);
      }
      return new Corner(xsb, ysb, dx0, dy0, xsb - 1, ysb + 1, dx0 + 1, dy0 - 1);
    }

    return new Corner(
        xsb, ysb, dx0, dy0,
        xsb + 1, ysb + 1, dx0 - 1 - 2 * SQUISH, dy0 - 1 - 2 * SQUISH
    );
  }

  /**
   * Resolves the base and extra vertices for a point inside the upper simplex.
   */
  private static Corner upperCorner(
      int xsb,
      int ysb,
      double xins,
      double yins,
      double inSum,
      double dx0,
      double dy0
  ) {
    double zins = 2 - inSum;
    int baseX = xsb + 1;
    int baseY = ysb + 1;
    double baseDx = dx0 - 1 - 2 * SQUISH;
    double baseDy = dy0 - 1 - 2 * SQUISH;

    if (zins < xins || zins < yins) {
      if (xins > yins) {
        return new Corner(
            baseX, baseY, baseDx, baseDy,
            xsb + 2, ysb, dx0 - 2 - 2 * SQUISH, dy0 - 2 * SQUISH
        );
      }
      return new Corner(
          baseX, baseY, baseDx, baseDy,
          xsb, ysb + 2, dx0 - 2 * SQUISH, dy0 - 2 - 2 * SQUISH
      );
    }

    return new Corner(baseX, baseY, baseDx, baseDy, xsb, ysb, dx0, dy0);
  }

  private double contribution(int xsb, int ysb, double dx, double dy) {
    double attenuation = 2 - dx * dx - dy * dy;
    if (attenuation <= 0) {
      return 0.0;
    }

    attenuation *= attenuation;
    return attenuation * attenuation * extrapolate(xsb, ysb, dx, dy);
  }

  private double extrapolate(int xsb, int ysb, double dx, double dy) {
    int index = permutation[(permutation[xsb & 0xFF] + ysb) & 0xFF] & 0x0E;
    return GRADIENTS[index] * dx + GRADIENTS[index + 1] * dy;
  }

  private static int floor(double value) {
    int truncated = (int) value;
    return value < truncated ? truncated - 1 : truncated;
  }

  /**
   * The two lattice vertices whose contributions still need adding.
   */
  private record Corner(
      int baseX,
      int baseY,
      double baseDx,
      double baseDy,
      int extraX,
      int extraY,
      double extraDx,
      double extraDy
  ) {}
}
