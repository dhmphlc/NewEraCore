package com.edysmajler.neweracore.world.structures;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.Vegetation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * World reads and writes for one structure placement, in absolute coordinates.
 *
 * <p>{@link ChunkContext} exists to keep a pass inside its own sixteen columns; this is
 * deliberately the opposite. A structure is placed as a whole shape only after every chunk of its
 * footprint has generated, so the writer is free to cross borders — every chunk it can touch
 * already exists, which means a read or write never generates terrain, only loads it.
 *
 * <p>All writes go in with physics off, same as the chunk pipeline, so a wreck does not cascade
 * into falling gravel and popped-off plants while it is being drawn. Removal therefore has the same
 * duty {@code ChunkContext.clear} has: whatever a removed block was holding up stays hanging unless
 * it is taken down too.
 *
 * <p>The random source is seeded from the site, so a structure always builds identically for its
 * seed no matter when the footprint happens to finish generating.
 */
public final class StructureField {

  private final World world;
  private final Random random;

  /**
   * Creates a field over one placement.
   *
   * @param world the world to write into
   * @param site the site being built, whose seed drives all texture
   */
  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP2", "PREDICTABLE_RANDOM"},
      justification = "The world is a live handle that must be kept to write blocks, and the "
          + "seeded java.util.Random is deliberate: a site must build identically for its seed."
  )
  public StructureField(World world, StructureSite site) {
    this.world = world;
    this.random = new Random(site.seed());
  }

  /**
   * Returns the site's random source.
   *
   * @return the seeded random
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Sharing the one seeded source is the point: every roll a definition makes "
          + "must come from the same deterministic stream."
  )
  public Random random() {
    return random;
  }

  /**
   * Returns the height of the actual ground in a column, ignoring anything growing on it.
   *
   * <p>Walks down past canopy, trunks, and undergrowth, exactly as the chunk pipeline does — the
   * highest block under a tree is the top of its canopy, and a wreck seated on a canopy height
   * floats. Water stops the walk, so a lake surface is the "ground" of its column.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the ground height
   */
  public int groundY(int blockX, int blockZ) {
    int y = world.getHighestBlockYAt(blockX, blockZ);
    int floor = Math.max(world.getMinHeight(), y - 48);

    while (y > floor && isCanopyOrClutter(typeAt(blockX, y, blockZ))) {
      y--;
    }

    return y;
  }

  /**
   * Returns whether a column's ground is a body of water or lava rather than land.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the column's surface is fluid or the ice frozen over it
   */
  public boolean isFluidColumn(int blockX, int blockZ) {
    return ChunkContext.isFluid(typeAt(blockX, groundY(blockX, blockZ), blockZ));
  }

  /**
   * Returns the material at a position.
   *
   * @param blockX absolute block x
   * @param y absolute height
   * @param blockZ absolute block z
   * @return the material, or air outside the world's height range
   */
  public Material typeAt(int blockX, int y, int blockZ) {
    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
      return Material.AIR;
    }

    return world.getBlockAt(blockX, y, blockZ).getType();
  }

  /**
   * Replaces a block without triggering physics.
   *
   * @param blockX absolute block x
   * @param y absolute height
   * @param blockZ absolute block z
   * @param material the material to place
   */
  public void set(int blockX, int y, int blockZ, Material material) {
    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
      return;
    }

    world.getBlockAt(blockX, y, blockZ).setType(material, false);
  }

  /**
   * Replaces a block with specific block data, without triggering physics.
   *
   * @param blockX absolute block x
   * @param y absolute height
   * @param blockZ absolute block z
   * @param data the block data to place
   */
  public void set(int blockX, int y, int blockZ, BlockData data) {
    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
      return;
    }

    world.getBlockAt(blockX, y, blockZ).setBlockData(data, false);
  }

  /**
   * Removes a block along with anything that was only resting on top of it.
   *
   * <p>Physics is off for every write, so nothing pops off by itself: a snow layer or flower left
   * over a carved hole hangs in mid-air unless it is taken down here.
   *
   * @param blockX absolute block x
   * @param y absolute height
   * @param blockZ absolute block z
   */
  public void clear(int blockX, int y, int blockZ) {
    set(blockX, y, blockZ, Material.AIR);

    for (int above = y + 1; isPerched(typeAt(blockX, above, blockZ)); above++) {
      set(blockX, above, blockZ, Material.AIR);
    }
  }

  /**
   * Returns the live block at a position, for the rare write that needs block state — a container
   * to fill, a sign to write.
   *
   * @param blockX absolute block x
   * @param y absolute height
   * @param blockZ absolute block z
   * @return the block
   */
  public Block blockAt(int blockX, int y, int blockZ) {
    return world.getBlockAt(blockX, y, blockZ);
  }

  private static boolean isCanopyOrClutter(Material material) {
    if (material.isAir() || !material.isSolid()) {
      return true;
    }

    return Vegetation.isStanding(material)
        || Tag.LOGS.isTagged(material)
        || Tag.LEAVES.isTagged(material)
        || Tag.WOOL_CARPETS.isTagged(material);
  }

  private static boolean isPerched(Material material) {
    return material == Material.SNOW || Vegetation.needsFooting(material);
  }
}
