package com.edysmajler.neweracore.world.history;

/**
 * One landmark site, in absolute world coordinates.
 *
 * <p>A location and an intention, nothing more. What stands there is a later problem, and a later
 * generator's — this record exists so that roads can aim at it, loot can be graded by it, and a
 * settlement can decide to shelter beside it, all before anything has been built.
 *
 * @param type what was here
 * @param centerX absolute block x of the site centre
 * @param centerZ absolute block z of the site centre
 */
public record Landmark(LandmarkType type, int centerX, int centerZ) {

  /**
   * Returns the radius in blocks this site claims.
   *
   * @return the footprint radius
   */
  public int radius() {
    return type.footprint();
  }

  /**
   * Returns the horizontal distance from this site's centre to a position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the distance in blocks
   */
  public double distanceTo(int blockX, int blockZ) {
    double dx = blockX - (double) centerX;
    double dz = blockZ - (double) centerZ;
    return Math.sqrt(dx * dx + dz * dz);
  }

  /**
   * Returns whether a position falls inside this site's footprint.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the position is on the site
   */
  public boolean covers(int blockX, int blockZ) {
    return distanceTo(blockX, blockZ) <= radius();
  }
}
