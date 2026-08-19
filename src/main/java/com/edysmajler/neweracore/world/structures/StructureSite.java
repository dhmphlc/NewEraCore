package com.edysmajler.neweracore.world.structures;

/**
 * One resolved structure site: what stands there, where, and which way it faces.
 *
 * <p>Derived by hashing a grid cell against the world seed, so it is a pure function of the seed:
 * every chunk the footprint touches computes the same site without loading anything, and a command
 * can report sites in terrain that has never been generated.
 *
 * @param structureId which structure stands here
 * @param centerX absolute block x of the site centre
 * @param centerZ absolute block z of the site centre
 * @param rotation quarter turns clockwise from the structure's authored facing, 0-3
 * @param radius how far from the centre the footprint can reach, in blocks
 * @param seed per-site seed for the texture inside the footprint
 */
public record StructureSite(
    String structureId,
    int centerX,
    int centerZ,
    int rotation,
    int radius,
    long seed
) {

  /**
   * Returns the distance from the site centre to a position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the distance in blocks
   */
  public double distanceTo(int blockX, int blockZ) {
    return Math.hypot(centerX - (double) blockX, centerZ - (double) blockZ);
  }

  /**
   * Returns whether this site's footprint reaches into a chunk.
   *
   * <p>Measured to the nearest point of the chunk, not its centre, so a footprint that only clips
   * a corner still counts — the chunk that misses a site it touches is the chunk that never
   * triggers its placement.
   *
   * @param chunkX chunk x coordinate
   * @param chunkZ chunk z coordinate
   * @return true when the footprint overlaps the chunk
   */
  public boolean touchesChunk(int chunkX, int chunkZ) {
    int originX = chunkX * 16;
    int originZ = chunkZ * 16;
    double nearestX = Math.clamp(centerX, originX, originX + 15);
    double nearestZ = Math.clamp(centerZ, originZ, originZ + 15);
    return distanceTo((int) nearestX, (int) nearestZ) <= radius;
  }
}
