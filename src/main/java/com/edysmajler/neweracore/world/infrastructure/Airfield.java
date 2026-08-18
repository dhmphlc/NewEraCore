package com.edysmajler.neweracore.world.infrastructure;

import com.edysmajler.neweracore.config.InfrastructureConfig;
import com.edysmajler.neweracore.world.history.Landmark;
import com.edysmajler.neweracore.world.history.LandmarkType;

/**
 * A runway: one rectangle, dead straight, and at exactly one height.
 *
 * <p>Roads are allowed to ride over hills, and they should — a real road follows the ground it
 * crosses. A runway is the opposite kind of object. An aircraft cannot land on a gradient, so the
 * ground gives way to the runway rather than the other way round: cut into the hill at one end,
 * filled up over the hollow at the other, level from end to end. That is what earthworks
 * <em>are</em>.
 *
 * <p><strong>Why a site can have an engineered grade when a route cannot.</strong> A route is
 * thousands of blocks long, and levelling one would mean knowing the terrain kilometres ahead —
 * which is exactly what a chunk forbidden from loading its neighbours cannot know. A runway is a
 * fixed rectangle at fixed coordinates, so its height can be derived from the seed alone. Every
 * chunk that touches it computes the same number without asking anyone, and then each one only has
 * to make its own columns agree with it. The height is anchored near sea level, which is the one
 * global height a world will tell you for free.
 *
 * <p>Which means the earthworks can be large. A runway laid across rising ground leaves a genuine
 * cutting at one end and an embankment at the other, and that is correct: it is what building an
 * airfield in hills actually costs.
 *
 * @param site the airport this belongs to
 * @param platformY the single height the whole runway sits at
 * @param cos the runway bearing, as a unit vector
 * @param sin the runway bearing, as a unit vector
 * @param length how long the strip is, in blocks
 * @param width how wide the strip is, in blocks
 */
public record Airfield(
    Landmark site,
    int platformY,
    double cos,
    double sin,
    int length,
    int width
) {

  /** How far the shoulder runs either side of the paved strip. */
  private static final int SHOULDER = 4;

  /**
   * Returns the airfield an airport has, or null for a landmark that is not one.
   *
   * @param site the landmark
   * @param config the infrastructure settings
   * @param seaLevel the world's sea level, the one global height available for free
   * @param worldSeed the world seed
   * @return the airfield, or null
   */
  public static Airfield at(
      Landmark site,
      InfrastructureConfig config,
      int seaLevel,
      long worldSeed
  ) {
    if (site.type() != LandmarkType.AIRPORT) {
      return null;
    }

    long hash = mix(worldSeed ^ 0xA12F1E1DL, site.centerX(), site.centerZ());

    // Any bearing, not just the compass points: a runway aligned to the axes on every airport in
    // the world would read as a template
    double angle = unitFrom(hash) * Math.PI;
    int lift = (int) (unitFrom(mix(hash, 7, 11)) * config.getRunwayLift());

    return new Airfield(
        site,
        seaLevel + lift,
        Math.cos(angle),
        Math.sin(angle),
        config.getRunwayLength(),
        config.getRunwayWidth()
    );
  }

  /**
   * Returns how far a position lies along the runway from its centre, in blocks.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the distance along the strip, signed
   */
  public double along(int blockX, int blockZ) {
    return (blockX - site.centerX()) * cos + (blockZ - site.centerZ()) * sin;
  }

  /**
   * Returns how far a position lies to the side of the runway's centre line, in blocks.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the distance across the strip, signed
   */
  public double across(int blockX, int blockZ) {
    return -(blockX - site.centerX()) * sin + (blockZ - site.centerZ()) * cos;
  }

  /**
   * Returns whether a position is on the levelled ground, paving and shoulders together.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the ground here is cut or filled to the platform
   */
  public boolean covers(int blockX, int blockZ) {
    return Math.abs(along(blockX, blockZ)) <= length / 2.0
        && Math.abs(across(blockX, blockZ)) <= width / 2.0 + SHOULDER;
  }

  /**
   * Returns whether a position is on the paved strip rather than its shoulder.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the position is runway rather than verge
   */
  public boolean isPaved(int blockX, int blockZ) {
    return covers(blockX, blockZ) && Math.abs(across(blockX, blockZ)) <= width / 2.0;
  }

  /**
   * Returns whether a position falls on the dashed centre line.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the position should be a marking
   */
  public boolean isMarking(int blockX, int blockZ) {
    if (Math.abs(across(blockX, blockZ)) > 0.7) {
      return false;
    }

    // Dashes: eight blocks of paint, eight of nothing, the length of the strip
    return Math.floorMod((int) Math.round(along(blockX, blockZ)), 16) < 8;
  }

  /**
   * Returns the highest the ground may be at a position without being in the way.
   *
   * <p>The obstacle surface: flat across the strip itself, then rising one block for every few
   * blocks further out. Anything above it is cut off. Levelling the runway alone was never enough —
   * an aircraft has to get down onto the strip and back off it, and it cannot fly through a hill
   * sitting at the end of the runway. This is what makes an airport a wide flat <em>place</em>
   * rather than a wide flat line.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @param reach how far past the strip the surface extends
   * @param slope how many blocks out buy one block up
   * @return the ceiling height, or {@link Integer#MAX_VALUE} outside the approach entirely
   */
  public int ceilingAt(int blockX, int blockZ, int reach, int slope) {
    double pastEnd = Math.max(0.0, Math.abs(along(blockX, blockZ)) - length / 2.0);
    double pastSide = Math.max(0.0, Math.abs(across(blockX, blockZ)) - width / 2.0 - SHOULDER);
    double outside = Math.hypot(pastEnd, pastSide);

    if (outside > reach) {
      return Integer.MAX_VALUE;
    }

    return platformY + (int) (outside / Math.max(1, slope));
  }

  /**
   * Returns how far from the centre this airfield can possibly reach, approaches included.
   *
   * @param approachReach how far past the strip the obstacle surface extends
   * @return the radius in blocks
   */
  public double reach(int approachReach) {
    return length / 2.0 + width + approachReach;
  }

  /**
   * Returns how far from the centre the built ground reaches.
   *
   * @return the radius in blocks
   */
  public double reach() {
    return length / 2.0 + width;
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
