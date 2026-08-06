package com.edysmajler.neweracore.world.terrain;

import com.edysmajler.neweracore.world.ChunkContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bukkit.Material;

/**
 * Facts about the shape of the land in one chunk.
 *
 * <p>This is the abstraction the engine was missing. Damage sprayed on from abstract noise always
 * reads as painted on, because nothing about it responds to the ground it sits on. Real marks of a
 * catastrophe follow the terrain: ash settles thick in hollows and blows off exposed ridges, steep
 * faces scour down to rock, low flat ground holds dust and dried mud. Driving the passes from
 * slope,
 * relief, and water proximity is what makes the result look <em>caused</em> rather than applied.
 *
 * <p>All values are derived from the chunk snapshot and cached, so the extra realism costs one pass
 * over 256 columns.
 */
public final class TerrainProbe {

  private static final int SIZE = ChunkContext.CHUNK_SIZE;

  /** How far to look for water when deciding whether a column is near a shoreline or bed. */
  private static final int WATER_SEARCH = 2;

  private final ChunkContext context;
  private final int[] slope = new int[SIZE * SIZE];
  private final int[] relief = new int[SIZE * SIZE];
  private final boolean[] nearWater = new boolean[SIZE * SIZE];

  private boolean probed;

  /**
   * Creates a probe over one chunk.
   *
   * @param context the chunk being transformed
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The probe reads heights straight from the live chunk context it is given; a "
          + "copy would defeat the point of sharing one snapshot per chunk."
  )
  public TerrainProbe(ChunkContext context) {
    this.context = context;
  }

  /**
   * Returns the steepness of a column, as the largest height difference to its four neighbours.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return the slope in blocks
   */
  public int slopeAt(int x, int z) {
    ensureProbed();
    return slope[index(x, z)];
  }

  /**
   * Returns how high a column stands relative to the land immediately around it.
   *
   * <p>Positive on ridges and knolls, negative in hollows and valley floors, near zero on open flat
   * ground. Ash gathers where this is negative and is stripped away where it is strongly positive.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return the relief in blocks
   */
  public int reliefAt(int x, int z) {
    ensureProbed();
    return relief[index(x, z)];
  }

  /**
   * Returns whether a column sits in or beside water.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return true when water is within a couple of blocks
   */
  public boolean isNearWater(int x, int z) {
    ensureProbed();
    return nearWater[index(x, z)];
  }

  /**
   * Returns whether a column is an exposed face that weather would strip bare.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @param scourSlope the slope at which scouring begins for this corruption level
   * @return true when the column should be scoured to rock
   */
  public boolean isScoured(int x, int z, int scourSlope) {
    return slopeAt(x, z) >= scourSlope;
  }

  /**
   * Returns whether a column is a hollow where ash and dust would pile up.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return true when the column is a sheltered low point
   */
  public boolean isHollow(int x, int z) {
    return reliefAt(x, z) <= -1 && slopeAt(x, z) <= 2;
  }

  private void ensureProbed() {
    if (probed) {
      return;
    }

    // Set the flag first: the helpers below read heights, not probe state, so there is no
    // recursion,
    // and this keeps the guard cheap.
    probed = true;

    for (int x = 0; x < SIZE; x++) {
      for (int z = 0; z < SIZE; z++) {
        // Ground height, not the highest block: canopy heights would make a flat forest look like
        // a cliff face and scour it to bare rock
        int here = context.groundY(x, z);
        int i = index(x, z);

        slope[i] = maxDrop(x, z, here);
        relief[i] = here - averageNeighbourHeight(x, z, here);
        nearWater[i] = waterWithinReach(x, z);
      }
    }
  }

  private int maxDrop(int x, int z, int here) {
    int drop = 0;
    drop = Math.max(drop, Math.abs(here - heightAt(x + 1, z, here)));
    drop = Math.max(drop, Math.abs(here - heightAt(x - 1, z, here)));
    drop = Math.max(drop, Math.abs(here - heightAt(x, z + 1, here)));
    drop = Math.max(drop, Math.abs(here - heightAt(x, z - 1, here)));
    return drop;
  }

  private int averageNeighbourHeight(int x, int z, int here) {
    int total = heightAt(x + 1, z, here)
        + heightAt(x - 1, z, here)
        + heightAt(x, z + 1, here)
        + heightAt(x, z - 1, here);
    return total / 4;
  }

  /**
   * Returns a neighbour's surface height, falling back to the column's own height at a chunk edge.
   *
   * <p>Reading across the border would force the neighbouring chunk to load, so edge columns simply
   * see themselves as flat. Ash treatment is continuous enough that the seam does not show.
   */
  private int heightAt(int x, int z, int fallback) {
    return context.inChunk(x, z) ? context.groundY(x, z) : fallback;
  }

  private boolean waterWithinReach(int x, int z) {
    for (int dx = -WATER_SEARCH; dx <= WATER_SEARCH; dx++) {
      for (int dz = -WATER_SEARCH; dz <= WATER_SEARCH; dz++) {
        int nx = x + dx;
        int nz = z + dz;
        if (!context.inChunk(nx, nz)) {
          continue;
        }

        if (context.typeAt(nx, context.groundY(nx, nz), nz) == Material.WATER) {
          return true;
        }
      }
    }

    return false;
  }

  private static int index(int x, int z) {
    return (x << 4) | z;
  }
}
