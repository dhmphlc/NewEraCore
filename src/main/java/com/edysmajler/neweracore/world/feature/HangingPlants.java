package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ChunkProcessor;
import com.edysmajler.neweracore.world.Vegetation;
import org.bukkit.Material;

/**
 * Takes down the vines that were left hanging from a canopy that is gone.
 *
 * <p>A vine holds on to the block beside it or the vine above it — never to anything below. Physics
 * is disabled for every write this engine makes, so when the canopy above a jungle vine is stripped
 * the vine does not fall: it stays exactly where it was, hanging out of open sky. A curtain of
 * green hanging off nothing is one of the loudest "a plugin did this" artefacts in the game, and it
 * appears wherever the engine takes a block away — a stripped canopy, a felled trunk, the wall of a
 * crater.
 *
 * <p>Which is why this is a <strong>pass of its own, running last</strong>, rather than a check
 * bolted onto whichever pass removed the anchor. Trees strip canopies, craters carve through walls,
 * and any future system that removes a block will do it again; a sweep at the end catches all of
 * them, including the ones nobody has written yet, and cannot be forgotten by the next pass someone
 * adds. The cost is one read of the scanned band, no writes unless something is actually adrift.
 *
 * <p>The sweep runs top down, which resolves whole hanging strands in a single pass: a vine held up
 * only by the vine above it is considered after that one has already gone.
 */
public final class HangingPlants implements ChunkProcessor {

  @Override
  public String name() {
    return "prune-hanging";
  }

  @Override
  public void process(ChunkContext context) {
    for (int x = 0; x < ChunkContext.CHUNK_SIZE; x++) {
      for (int z = 0; z < ChunkContext.CHUNK_SIZE; z++) {
        pruneColumn(context, x, z);
      }
    }
  }

  private static void pruneColumn(ChunkContext context, int x, int z) {
    int floor = context.scanFloor(x, z);

    for (int y = context.surfaceY(x, z) + Vegetation.REACH; y >= floor; y--) {
      Material material = context.typeAt(x, y, z);

      if (Vegetation.isHanging(material) && !isAnchored(context, x, y, z, material)) {
        context.clear(x, y, z);
      }
    }
  }

  /**
   * Returns whether a hanging plant still has something to hold on to.
   *
   * <p>A column on the chunk edge is treated as anchored. Reading across the border would force the
   * neighbouring chunk to load, which this engine never does, and the safe assumption when the
   * answer is unknown is that the vine is fine: pruning one that was holding on to a block just
   * over the border would delete something the player can plainly see is attached.
   */
  private static boolean isAnchored(
      ChunkContext context,
      int x,
      int y,
      int z,
      Material plant
  ) {
    // Hanging from more of itself. Tested by kind rather than by exact material, because a strand
    // can change material along its length — a cave vine bearing berries hangs from a plain one.
    if (Vegetation.isHanging(context.typeAt(x, y + 1, z))) {
      return true;
    }

    // Only vines are strictly downward-hanging. Lichen and its relatives will also sit on the top
    // face of a block, so for those the floor counts, and pruning one that is plainly stuck to the
    // ground would be a worse artefact than the one this pass exists to remove.
    if (plant != Material.VINE && isAnchor(context, x, y - 1, z)) {
      return true;
    }

    return isAnchor(context, x, y + 1, z)
        || isAnchor(context, x + 1, y, z)
        || isAnchor(context, x - 1, y, z)
        || isAnchor(context, x, y, z + 1)
        || isAnchor(context, x, y, z - 1);
  }

  /**
   * Returns whether a position holds a block a plant could cling to.
   *
   * <p>A solid block, excluding the solid plants: bamboo and a cactus have no full face to grip,
   * and a vine clinging to a mushroom cap that is itself only there on sufferance is not worth
   * keeping.
   */
  private static boolean isAnchor(ChunkContext context, int x, int y, int z) {
    if (!context.inChunk(x, z)) {
      return true;
    }

    Material material = context.typeAt(x, y, z);
    return material.isSolid() && !Vegetation.isStanding(material);
  }
}
