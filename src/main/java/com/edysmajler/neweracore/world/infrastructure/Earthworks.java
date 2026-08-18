package com.edysmajler.neweracore.world.infrastructure;

import com.edysmajler.neweracore.world.ChunkContext;
import org.bukkit.Material;

/**
 * Makes the ground agree with something that has to be level.
 *
 * <p>Cut what stands above the platform, fill what falls below it, and lay the surface. One column
 * at a time, with no chunk needing to know anything about the next: the platform height came from
 * the seed, so every chunk already agrees on the answer and only has to bring its own ground to it.
 *
 * <p>The fill is stopped at {@link #MAX_FILL} blocks. Without a limit, a runway whose end runs out
 * over a deep valley would pour a solid column of rock the whole way down, and one that reaches
 * open water would keep going to the seabed. A hole under the far end reads as subsidence, which
 * this world has plenty of; a hundred-block plinth of tuff reads as a bug.
 */
public final class Earthworks {

  /** How deep a hollow may be filled before the far end is simply left unbuilt. */
  private static final int MAX_FILL = 40;

  private static final Material FILL = Material.TUFF;

  private Earthworks() {}

  /**
   * Brings one column to a height and returns whether it got there.
   *
   * @param context the chunk being transformed
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @param platformY the height the ground has to reach
   * @param clearance how much headroom to open above the platform
   * @return true when the column now stands at the platform
   */
  public static boolean levelTo(
      ChunkContext context,
      int x,
      int z,
      int platformY,
      int clearance
  ) {
    int groundY = context.groundY(x, z);

    if (groundY > platformY) {
      cutDown(context, x, z, platformY);
      return true;
    }

    return fillUp(context, x, z, groundY, platformY) && clear(context, x, z, platformY, clearance);
  }

  /**
   * Opens the air above a platform, so nothing stands on it.
   *
   * @param context the chunk being transformed
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @param platformY the platform height
   * @param clearance how far above the platform to clear
   * @return true always, so it can be chained
   */
  public static boolean clear(
      ChunkContext context,
      int x,
      int z,
      int platformY,
      int clearance
  ) {
    for (int y = platformY + 1; y <= platformY + clearance; y++) {
      if (!context.typeAt(x, y, z).isAir()) {
        context.clear(x, y, z);
      }
    }

    return true;
  }

  /**
   * Takes off everything above a height, leaving what is below untouched.
   *
   * <p>What an approach surface needs: a hill in front of a runway has to lose its top, and nothing
   * else about it has to change.
   *
   * @param context the chunk being transformed
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @param ceiling the highest the ground may be
   */
  public static void clearAbove(ChunkContext context, int x, int z, int ceiling) {
    cutDown(context, x, z, ceiling);
  }

  /**
   * Takes the hillside off, all the way up to whatever was standing on it.
   */
  private static void cutDown(ChunkContext context, int x, int z, int platformY) {
    int top = context.surfaceY(x, z) + 8;

    for (int y = top; y > platformY; y--) {
      if (!context.typeAt(x, y, z).isAir()) {
        context.clear(x, y, z);
      }
    }
  }

  /**
   * Builds the embankment up to the platform, water and all.
   */
  private static boolean fillUp(
      ChunkContext context,
      int x,
      int z,
      int groundY,
      int platformY
  ) {
    if (platformY - groundY > MAX_FILL) {
      return false;
    }

    for (int y = platformY; y > groundY; y--) {
      context.set(x, y, z, FILL);
    }

    return true;
  }
}
