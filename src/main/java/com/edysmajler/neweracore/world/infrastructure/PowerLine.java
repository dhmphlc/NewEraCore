package com.edysmajler.neweracore.world.infrastructure;

import com.edysmajler.neweracore.config.InfrastructureConfig;
import com.edysmajler.neweracore.world.ChunkContext;
import org.bukkit.Material;

/**
 * Stands the pylons up and runs the wire between them.
 *
 * <p>A power line is the cheapest piece of infrastructure to read from a distance and the most
 * useful: it tells a player, from a ridge a hundred blocks away, that something worth powering is
 * at the end of it. Since the line joins a dam to whatever it fed, following one is a decision
 * rather than a wander.
 *
 * <p>The wire is hung at a fixed height above the ground rather than at a fixed height in the
 * world, so it rides the terrain like real cable and needs no agreement between chunks about
 * anything. Pylons land on a fixed lattice of world coordinates for the same reason: every chunk
 * works out the same answer for the same block without asking anyone.
 */
public final class PowerLine {

  private static final Material PYLON = Material.IRON_BARS;
  private static final Material WIRE = Material.IRON_CHAIN;

  private PowerLine() {}

  /**
   * Builds whatever a power line has at one of its centre columns.
   *
   * @param context the chunk being transformed
   * @param config the infrastructure settings
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   */
  public static void build(ChunkContext context, InfrastructureConfig config, int x, int z) {
    if (context.isFluidColumn(x, z)) {
      return;
    }

    int groundY = context.groundY(x, z);
    int wireY = groundY + config.getPylonHeight();

    int blockX = context.blockX(x);
    int blockZ = context.blockZ(z);

    if ((blockX + blockZ) % config.getPylonSpacing() == 0) {
      raisePylon(context, x, groundY, wireY, z);
      return;
    }

    if (context.typeAt(x, wireY, z).isAir()) {
      context.set(x, wireY, z, WIRE);
    }
  }

  private static void raisePylon(ChunkContext context, int x, int groundY, int wireY, int z) {
    for (int y = groundY + 1; y <= wireY; y++) {
      if (context.typeAt(x, y, z).isAir()) {
        context.set(x, y, z, PYLON);
      }
    }

    // A crossarm, so the silhouette is a pylon rather than a post
    for (int offset = -2; offset <= 2; offset++) {
      if (offset == 0 || !context.inChunk(x + offset, z)) {
        continue;
      }

      if (context.typeAt(x + offset, wireY, z).isAir()) {
        context.set(x + offset, wireY, z, PYLON);
      }
    }
  }
}
