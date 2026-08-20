package com.edysmajler.neweracore.world.roads;

/**
 * One abandoned car: where it stopped and which way it points.
 *
 * <p>Sampled along a road segment as a pure function of the seed, so every chunk its small
 * footprint touches agrees it is there. The heading is a free angle rather than a quarter turn —
 * cars pulled over, slewed, or crashed, and a car park of perfectly grid-aligned wrecks is the
 * placed-not-abandoned look.
 *
 * @param x absolute block x of the car's centre
 * @param z absolute block z of the car's centre
 * @param heading the direction the car points, in radians
 * @param seed per-car seed for its body, colour, and damage
 */
public record CarSite(int x, int z, double heading, long seed) {

  /** How far a car and its clearing can reach from its centre. */
  public static final int RADIUS = 5;

  /**
   * Returns whether this car's footprint reaches into a chunk.
   *
   * @param chunkX chunk x coordinate
   * @param chunkZ chunk z coordinate
   * @return true when the footprint overlaps the chunk
   */
  public boolean touchesChunk(int chunkX, int chunkZ) {
    int originX = chunkX * 16;
    int originZ = chunkZ * 16;
    double nearestX = Math.clamp(x, originX, originX + 15);
    double nearestZ = Math.clamp(z, originZ, originZ + 15);
    return Math.hypot(x - nearestX, z - nearestZ) <= RADIUS;
  }
}
