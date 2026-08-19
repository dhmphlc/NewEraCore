package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.world.ChunkContext;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.Orientable;

/**
 * One pass over a chunk collecting everything the tree features need.
 *
 * <p>Trunks, logs, and leaves are gathered together rather than each feature scanning the chunk
 * again, which keeps the cost at a single sweep of the scanned band no matter how many tree effects
 * run.
 *
 * @param trees trunk bases found standing on ground
 * @param logs every log position in the scanned band
 * @param leaves every leaf position in the scanned band
 */
public record TreeScan(List<Tree> trees, List<BlockPosition> logs, List<BlockPosition> leaves) {

  /**
   * Copies the lists so a scan cannot be mutated after it is taken.
   */
  public TreeScan {
    trees = List.copyOf(trees);
    logs = List.copyOf(logs);
    leaves = List.copyOf(leaves);
  }

  /**
   * Scans a chunk.
   *
   * @param context the chunk being transformed
   * @return the collected trunks, logs, and leaves
   */
  public static TreeScan of(ChunkContext context) {
    List<Tree> trees = new ArrayList<>();
    List<BlockPosition> logs = new ArrayList<>();
    List<BlockPosition> leaves = new ArrayList<>();

    for (int x = 0; x < ChunkContext.CHUNK_SIZE; x++) {
      for (int z = 0; z < ChunkContext.CHUNK_SIZE; z++) {
        collectColumn(context, x, z, trees, logs, leaves);
      }
    }

    return new TreeScan(trees, logs, leaves);
  }

  private static void collectColumn(
      ChunkContext context,
      int x,
      int z,
      List<Tree> trees,
      List<BlockPosition> logs,
      List<BlockPosition> leaves
  ) {
    int floor = context.scanFloor(x, z);

    for (int y = context.surfaceY(x, z); y >= floor; y--) {
      Material material = context.typeAt(x, y, z);

      if (Tag.LEAVES.isTagged(material)) {
        leaves.add(new BlockPosition(x, y, z));
        continue;
      }

      if (!Tag.LOGS.isTagged(material)) {
        continue;
      }

      logs.add(new BlockPosition(x, y, z));
      // A trunk base is a log standing upright on the ground. The axis check is what keeps the
      // generator's fallen trunks out: a log lying on its side also sits on dirt, and treating
      // each block of the run as a one-block tree turned fallen trees into rows of stumps.
      if (isGround(context.typeAt(x, y - 1, z)) && isUpright(context, x, y, z)) {
        trees.add(new Tree(x, y, z));
      }
    }
  }

  private static boolean isUpright(ChunkContext context, int x, int y, int z) {
    return !(context.generatedDataAt(x, y, z) instanceof Orientable orientable)
        || orientable.getAxis() == Axis.Y;
  }

  private static boolean isGround(Material material) {
    return Tag.DIRT.isTagged(material)
        || Tag.SAND.isTagged(material)
        || material == Material.STONE;
  }
}
