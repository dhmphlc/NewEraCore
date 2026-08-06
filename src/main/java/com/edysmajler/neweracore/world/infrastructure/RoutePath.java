package com.edysmajler.neweracore.world.infrastructure;

/**
 * The line a route actually follows between two places.
 *
 * <p>A gentle curve rather than a straight edge, because a network of perfectly straight lines
 * between points reads as a diagram. The curve is a quadratic Bézier whose control point is pushed
 * sideways by an amount hashed from the two endpoints, so it bends by a believable amount and
 * always by the same amount: the path is a pure function of the places it joins.
 *
 * <p>That purity is the entire trick. A road is thousands of blocks long and has to be drawn one
 * sixteen-block chunk at a time, by chunks that are forbidden from looking at each other. Because
 * any chunk can recompute the whole curve from the two endpoints alone, each one can draw its own
 * slice and the slices line up exactly — the same reason a huge crater can cross a chunk border.
 *
 * <p>Stored as points sampled a block apart, which is what drawing wants: walk the samples, paint
 * what is near them. Asking each of a chunk's 256 columns for its distance to the whole curve
 * instead would be hundreds of thousands of comparisons per chunk.
 */
public final class RoutePath {

  /** How far the curve may wander sideways, as a share of its length. */
  private static final double WANDER = 0.16;

  /** Spacing between samples, in blocks. Below one so the painted strip has no gaps. */
  private static final double STEP = 0.8;

  /** Stride used when only a rough answer is needed, in samples. */
  private static final int COARSE = 8;

  private final double[] xs;
  private final double[] zs;
  private final double minX;
  private final double minZ;
  private final double maxX;
  private final double maxZ;
  private final double length;

  private RoutePath(double[] xs, double[] zs, double length) {
    this.xs = xs;
    this.zs = zs;
    this.length = length;

    double lowX = Double.MAX_VALUE;
    double lowZ = Double.MAX_VALUE;
    double highX = -Double.MAX_VALUE;
    double highZ = -Double.MAX_VALUE;

    for (int i = 0; i < xs.length; i++) {
      lowX = Math.min(lowX, xs[i]);
      lowZ = Math.min(lowZ, zs[i]);
      highX = Math.max(highX, xs[i]);
      highZ = Math.max(highZ, zs[i]);
    }

    this.minX = lowX;
    this.minZ = lowZ;
    this.maxX = highX;
    this.maxZ = highZ;
  }

  /**
   * Builds the path between two places.
   *
   * @param fromX absolute block x of one end
   * @param fromZ absolute block z of one end
   * @param toX absolute block x of the other end
   * @param toZ absolute block z of the other end
   * @param worldSeed the world seed, so a world's roads bend its own way
   * @return the path
   */
  public static RoutePath between(int fromX, int fromZ, int toX, int toZ, long worldSeed) {
    double dx = toX - (double) fromX;
    double dz = toZ - (double) fromZ;
    double span = Math.sqrt(dx * dx + dz * dz);

    if (span < 1.0) {
      return new RoutePath(new double[] {fromX}, new double[] {fromZ}, 0.0);
    }

    // Push the control point out along the perpendicular, by a hashed share of the length
    double bend = (unitFrom(mix(worldSeed, fromX ^ toZ, toX ^ fromZ)) - 0.5) * 2.0 * WANDER * span;
    double controlX = (fromX + toX) / 2.0 - dz / span * bend;
    double controlZ = (fromZ + toZ) / 2.0 + dx / span * bend;

    int steps = Math.max(1, (int) Math.ceil(span * 1.1 / STEP));
    double[] xs = new double[steps + 1];
    double[] zs = new double[steps + 1];

    for (int i = 0; i <= steps; i++) {
      double t = i / (double) steps;
      double inverse = 1.0 - t;
      double weight = 2.0 * inverse * t;

      xs[i] = inverse * inverse * fromX + weight * controlX + t * t * toX;
      zs[i] = inverse * inverse * fromZ + weight * controlZ + t * t * toZ;
    }

    return new RoutePath(xs, zs, span);
  }

  /**
   * Returns how many sampled points the path has.
   *
   * @return the sample count
   */
  public int sampleCount() {
    return xs.length;
  }

  /**
   * Returns the x of one sampled point.
   *
   * @param index the sample index
   * @return the absolute block x
   */
  public double sampleX(int index) {
    return xs[index];
  }

  /**
   * Returns the z of one sampled point.
   *
   * @param index the sample index
   * @return the absolute block z
   */
  public double sampleZ(int index) {
    return zs[index];
  }

  /**
   * Returns the straight-line distance between the two ends.
   *
   * @return the length in blocks
   */
  public double length() {
    return length;
  }

  /**
   * Returns how far a curve may stray from the straight line between its ends.
   *
   * <p>Lets a caller rule a route out before building it. A quadratic Bézier reaches half way to
   * its control point, and the control point is offset by at most {@link #WANDER} of the length —
   * so anything further away than this from the straight line cannot be near the curve either. That
   * test costs nothing, while building the curve to find out costs thousands of points.
   *
   * @param length the straight-line distance between the ends
   * @return the widest the curve can stray, in blocks
   */
  public static double maxDeviation(double length) {
    return WANDER * length * 0.5 + 1.0;
  }

  /**
   * Returns whether the path comes near a position.
   *
   * <p>Walks every eighth sample, which is enough to decide: consecutive samples are less than a
   * block apart, so nothing can hide between two of them. A bounding box was tried first and was
   * badly wrong — the box around a long diagonal route covers ground the route never goes near, so
   * three quarters of all chunks believed they had a road in them and paid to find out otherwise.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @param reach how close counts as near, in blocks
   * @return true when the path comes within reach
   */
  public boolean reaches(int blockX, int blockZ, double reach) {
    if (blockX < minX - reach || blockX > maxX + reach
        || blockZ < minZ - reach || blockZ > maxZ + reach) {
      return false;
    }

    double limit = reach + COARSE * STEP;
    double limitSquared = limit * limit;

    for (int i = 0; i < xs.length; i += COARSE) {
      double dx = xs[i] - blockX;
      double dz = zs[i] - blockZ;

      if (dx * dx + dz * dz <= limitSquared) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns the distance from a position to the straight line between two points.
   *
   * <p>The cheap half of the test above: no curve is built, so it can be run against every
   * candidate pair a chunk can see.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @param fromX absolute block x of one end
   * @param fromZ absolute block z of one end
   * @param toX absolute block x of the other end
   * @param toZ absolute block z of the other end
   * @return the distance in blocks
   */
  public static double distanceToLine(
      int blockX,
      int blockZ,
      int fromX,
      int fromZ,
      int toX,
      int toZ
  ) {
    double dx = toX - (double) fromX;
    double dz = toZ - (double) fromZ;
    double lengthSquared = dx * dx + dz * dz;

    if (lengthSquared < 1.0) {
      return Math.hypot(blockX - (double) fromX, blockZ - (double) fromZ);
    }

    double along = ((blockX - fromX) * dx + (blockZ - fromZ) * dz) / lengthSquared;
    double clamped = Math.max(0.0, Math.min(1.0, along));

    return Math.hypot(fromX + clamped * dx - blockX, fromZ + clamped * dz - blockZ);
  }

  /**
   * Returns the distance from a position to the nearest point on the path.
   *
   * <p>Walks every sample, so it is for asking questions — how far is the nearest road — rather
   * than for drawing. Drawing walks the samples instead and paints around them.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the distance in blocks
   */
  public double distanceTo(int blockX, int blockZ) {
    double nearest = Double.MAX_VALUE;

    for (int i = 0; i < xs.length; i++) {
      double dx = xs[i] - blockX;
      double dz = zs[i] - blockZ;
      nearest = Math.min(nearest, dx * dx + dz * dz);
    }

    return Math.sqrt(nearest);
  }

  private static long mix(long seed, long x, long z) {
    long hash = seed;
    hash ^= x * 0x9E3779B97F4A7C15L;
    hash ^= z * 0xC2B2AE3D27D4EB4FL;
    hash ^= hash >>> 33;
    hash *= 0xFF51AFD7ED558CCDL;
    hash ^= hash >>> 33;
    hash *= 0xC4CEB9FE1A85EC53L;
    hash ^= hash >>> 33;
    return hash;
  }

  private static double unitFrom(long hash) {
    return (hash >>> 11) * 0x1.0p-53;
  }
}
