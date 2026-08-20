package com.edysmajler.neweracore.world.roads;

/**
 * One stretch of road between two network nodes, as a gently bent polyline.
 *
 * <p>A segment's five points are fixed at creation from the seed, so every chunk the road crosses
 * computes exactly the same line — the same contract structure sites keep, stretched into one
 * dimension. All queries are pure geometry: nearest distance for the paving pass, arc-length
 * sampling for the cars and poles strung along it.
 */
public final class RoadSegment {

  private final RoadKind kind;
  private final long seed;
  private final double[] xs;
  private final double[] zs;
  private final double[] arcStarts;
  private final double length;
  private final double minX;
  private final double minZ;
  private final double maxX;
  private final double maxZ;

  /**
   * What the nearest point of a segment looks like from a column.
   *
   * @param distance blocks from the column to the centreline
   * @param arc how far along the road the nearest point lies, in blocks from the start
   * @param dirX x of the road direction at that point, unit length
   * @param dirZ z of the road direction at that point, unit length
   */
  public record Nearest(double distance, double arc, double dirX, double dirZ) {}

  /**
   * Creates a segment over its polyline points.
   *
   * @param kind the road class
   * @param seed the per-segment seed, which also identifies the segment
   * @param xs polyline point x coordinates, at least two
   * @param zs polyline point z coordinates, same length as xs
   */
  public RoadSegment(RoadKind kind, long seed, double[] xs, double[] zs) {
    if (xs.length < 2 || xs.length != zs.length) {
      throw new IllegalArgumentException("A road segment needs matching polyline points");
    }

    this.kind = kind;
    this.seed = seed;
    this.xs = xs.clone();
    this.zs = zs.clone();

    this.arcStarts = new double[xs.length];
    double total = 0.0;
    double lowX = xs[0];
    double lowZ = zs[0];
    double highX = xs[0];
    double highZ = zs[0];

    for (int i = 1; i < xs.length; i++) {
      arcStarts[i - 1] = total;
      total += Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
      lowX = Math.min(lowX, xs[i]);
      lowZ = Math.min(lowZ, zs[i]);
      highX = Math.max(highX, xs[i]);
      highZ = Math.max(highZ, zs[i]);
    }

    this.length = total;
    this.minX = lowX;
    this.minZ = lowZ;
    this.maxX = highX;
    this.maxZ = highZ;
  }

  /**
   * Returns the road class.
   *
   * @return the kind
   */
  public RoadKind kind() {
    return kind;
  }

  /**
   * Returns the per-segment seed, which doubles as the segment's identity.
   *
   * @return the seed
   */
  public long seed() {
    return seed;
  }

  /**
   * Returns the road's length along its polyline.
   *
   * @return the length in blocks
   */
  public double length() {
    return length;
  }

  /**
   * Returns whether this segment could reach a block-aligned box.
   *
   * <p>A cheap bounding-box test so a chunk can reject the many far-away segments before doing
   * real geometry on the few nearby ones.
   *
   * @param boxMinX smallest block x of the box
   * @param boxMinZ smallest block z of the box
   * @param boxMaxX largest block x of the box
   * @param boxMaxZ largest block z of the box
   * @param pad how far past the centreline the road's influence reaches
   * @return true when the padded bounds overlap the box
   */
  public boolean nearBox(int boxMinX, int boxMinZ, int boxMaxX, int boxMaxZ, double pad) {
    return minX - pad <= boxMaxX && maxX + pad >= boxMinX
        && minZ - pad <= boxMaxZ && maxZ + pad >= boxMinZ;
  }

  /**
   * Returns the nearest point of the centreline to a column.
   *
   * @param x absolute block x
   * @param z absolute block z
   * @return the nearest point, its arc position, and the road direction there
   */
  public Nearest nearest(double x, double z) {
    double bestDistance = Double.MAX_VALUE;
    double bestArc = 0.0;
    double bestDirX = 1.0;
    double bestDirZ = 0.0;

    for (int i = 0; i < xs.length - 1; i++) {
      double ax = xs[i];
      double az = zs[i];
      double bx = xs[i + 1];
      double bz = zs[i + 1];
      double dx = bx - ax;
      double dz = bz - az;
      double lengthSq = dx * dx + dz * dz;

      double t = lengthSq == 0.0
          ? 0.0
          : Math.clamp(((x - ax) * dx + (z - az) * dz) / lengthSq, 0.0, 1.0);

      double px = ax + t * dx;
      double pz = az + t * dz;
      double distance = Math.hypot(x - px, z - pz);

      if (distance < bestDistance) {
        double partLength = Math.sqrt(lengthSq);
        bestDistance = distance;
        bestArc = arcStarts[i] + t * partLength;
        if (partLength > 0.0) {
          bestDirX = dx / partLength;
          bestDirZ = dz / partLength;
        }
      }
    }

    return new Nearest(bestDistance, bestArc, bestDirX, bestDirZ);
  }

  /**
   * Returns the point a given distance along the road.
   *
   * @param arc blocks from the segment start, clamped to the road
   * @return the point as {x, z}
   */
  public double[] pointAt(double arc) {
    int part = partFor(arc);
    double partStart = arcStarts[part];
    double partLength = Math.hypot(xs[part + 1] - xs[part], zs[part + 1] - zs[part]);
    double t = partLength == 0.0 ? 0.0 : Math.clamp((arc - partStart) / partLength, 0.0, 1.0);

    return new double[] {
        xs[part] + t * (xs[part + 1] - xs[part]),
        zs[part] + t * (zs[part + 1] - zs[part])
    };
  }

  /**
   * Returns the road direction a given distance along it.
   *
   * @param arc blocks from the segment start, clamped to the road
   * @return the unit direction as {x, z}
   */
  public double[] directionAt(double arc) {
    int part = partFor(arc);
    double dx = xs[part + 1] - xs[part];
    double dz = zs[part + 1] - zs[part];
    double partLength = Math.hypot(dx, dz);

    return partLength == 0.0
        ? new double[] {1.0, 0.0}
        : new double[] {dx / partLength, dz / partLength};
  }

  private int partFor(double arc) {
    for (int i = xs.length - 2; i > 0; i--) {
      if (arc >= arcStarts[i]) {
        return i;
      }
    }
    return 0;
  }
}
