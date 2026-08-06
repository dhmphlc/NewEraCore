package com.edysmajler.neweracore.world.feature;

/**
 * A huge impact site, positioned in absolute world coordinates.
 *
 * @param centerX absolute block x of the impact centre
 * @param centerZ absolute block z of the impact centre
 * @param radius radius in blocks
 */
public record CraterSite(int centerX, int centerZ, int radius) {

  /**
   * Returns the horizontal distance from this site's centre to a column.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the distance in blocks
   */
  public double distanceTo(int blockX, int blockZ) {
    double dx = blockX - centerX;
    double dz = blockZ - centerZ;
    return Math.sqrt(dx * dx + dz * dz);
  }

  /**
   * Returns whether this site reaches a chunk at all, including its ejecta apron.
   *
   * @param originX absolute block x of the chunk's corner
   * @param originZ absolute block z of the chunk's corner
   * @param reach how far past the radius to consider
   * @return true when the chunk is affected
   */
  public boolean reaches(int originX, int originZ, double reach) {
    double limit = radius * reach + 16.0;
    return Math.abs(centerX - (originX + 8)) <= limit
        && Math.abs(centerZ - (originZ + 8)) <= limit;
  }
}
