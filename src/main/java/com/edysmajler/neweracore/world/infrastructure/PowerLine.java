package com.edysmajler.neweracore.world.infrastructure;

import com.edysmajler.neweracore.config.InfrastructureConfig;
import com.edysmajler.neweracore.world.ChunkContext;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

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
   * @param alongX whether the line runs more east-west than north-south here
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   */
  public static void build(
      ChunkContext context,
      InfrastructureConfig config,
      boolean alongX,
      int x,
      int z
  ) {
    if (context.isFluidColumn(x, z)) {
      return;
    }

    int groundY = context.groundY(x, z);
    int wireY = groundY + config.getPylonHeight();

    int blockX = context.blockX(x);
    int blockZ = context.blockZ(z);

    if ((blockX + blockZ) % config.getPylonSpacing() == 0) {
      raisePylon(context, x, groundY, wireY, z, alongX);
      return;
    }

    if (context.typeAt(x, wireY, z).isAir()) {
      context.set(x, wireY, z, wire(alongX ? Axis.X : Axis.Z));
    }
  }

  /**
   * Returns a chain lying along an axis.
   *
   * <p>A chain hangs vertically unless it is told not to, which is what a run of them along a power
   * line looked like: a row of short dangling links rather than a cable. Laying them along the
   * line's own direction is the whole difference between the two.
   */
  private static BlockData wire(Axis axis) {
    BlockData data = Bukkit.createBlockData(WIRE);

    if (data instanceof Orientable orientable) {
      orientable.setAxis(axis);
      return orientable;
    }

    return data;
  }

  private static void raisePylon(
      ChunkContext context,
      int x,
      int groundY,
      int wireY,
      int z,
      boolean alongX
  ) {
    for (int y = groundY + 1; y <= wireY; y++) {
      if (context.typeAt(x, y, z).isAir()) {
        context.set(x, y, z, PYLON);
      }
    }

    // A crossarm across the line, not along it, so the silhouette reads as a pylon rather than a
    // post
    for (int offset = -2; offset <= 2; offset++) {
      int armX = alongX ? x : x + offset;
      int armZ = alongX ? z + offset : z;

      if (offset == 0 || !context.inChunk(armX, armZ)) {
        continue;
      }

      if (context.typeAt(armX, wireY, armZ).isAir()) {
        context.set(armX, wireY, armZ, PYLON);
      }
    }
  }
}
