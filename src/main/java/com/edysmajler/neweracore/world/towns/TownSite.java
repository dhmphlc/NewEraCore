package com.edysmajler.neweracore.world.towns;

import java.util.List;

/**
 * One resolved town: where it stands and which ways its streets run.
 *
 * <p>Like a structure site, a town is a pure function of the seed: every chunk its footprint
 * touches computes the same town without loading anything, and {@code /nec locate town} can answer
 * about terrain that has never generated. The streets point at the neighbouring towns — unpaved
 * lanes the houses line up along, so a town reads as a settlement with a direction rather than a
 * scatter of boxes.
 *
 * @param centerX absolute block x of the town centre
 * @param centerZ absolute block z of the town centre
 * @param radius how far from the centre the town's buildings can reach, in blocks
 * @param seed per-town seed for its layout and ruin texture
 * @param streets the directions the streets leave the centre in, unit vectors; empty for a town
 *     whose neighbours all came to nothing
 */
public record TownSite(
    int centerX,
    int centerZ,
    int radius,
    long seed,
    List<Heading> streets
) {

  /** A unit direction in the horizontal plane. */
  public record Heading(double x, double z) {}

  /**
   * Copies the street list so a site is immutable from birth.
   */
  public TownSite {
    streets = List.copyOf(streets);
  }

  /**
   * Returns whether this town's footprint reaches into a chunk.
   *
   * <p>Measured to the nearest point of the chunk, like a structure site: the chunk that misses a
   * town it touches may be the last of the footprint to generate, and then the town never places.
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
    return Math.hypot(centerX - nearestX, centerZ - nearestZ) <= radius;
  }

  /**
   * Returns the distance from the town centre to a position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the distance in blocks
   */
  public double distanceTo(int blockX, int blockZ) {
    return Math.hypot(centerX - (double) blockX, centerZ - (double) blockZ);
  }
}
