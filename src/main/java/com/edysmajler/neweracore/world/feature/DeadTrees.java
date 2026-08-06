package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ash.AshPalette;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Replaces living trees with dead ones.
 *
 * <p>This is the correction that mattered most. Every earlier version <em>thinned</em> canopies: it
 * deleted a share of each tree's leaves and left the rest. A vanilla tree shape with holes punched
 * through it is the clearest griefing signature there is, and no amount of tuning the share fixes
 * it,
 * because the shape is still a healthy tree's shape.
 *
 * <p>So nothing is thinned. A tree is either alive and completely untouched, or dead — and a dead
 * tree
 * has <strong>no leaves at all</strong>, a charred trunk, and a few bare branch stubs that give it
 * a
 * recognisable dead silhouette. Some come down entirely, leaving a stump and a trunk lying in the
 * ash.
 *
 * <p>Which trees die is decided by the blight field, so whole stands share a fate and living groves
 * survive as coherent islands rather than as scattered individuals.
 */
public final class DeadTrees {

  /** How far above a trunk base leaves and branches still belong to that tree. */
  private static final int CROWN_HEIGHT = 26;

  /** Longest branch stub grown on a dead trunk. */
  private static final int BRANCH_LENGTH = 2;

  private DeadTrees() {}

  /**
   * Kills the doomed stands in a chunk and strips their crowns.
   *
   * @param context the chunk being transformed
   * @param scan the chunk's collected trunks, logs, and leaves
   * @param palette the biome's materials
   */
  public static void apply(ChunkContext context, TreeScan scan, AshPalette palette) {
    CorruptionProfile profile = context.profile();

    stripDeadCanopy(context, profile, scan);

    for (Tree tree : scan.trees()) {
      if (isLiving(context, profile, tree.x(), tree.z())) {
        continue;
      }

      if (context.chance(profile.collapseShare())) {
        collapse(context, palette, tree);
      } else {
        raiseSnag(context, palette, profile, tree);
      }
    }
  }

  /**
   * Returns whether the stand at a column survived.
   *
   * @param context the chunk being transformed
   * @param profile the effective rules
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return true when the trees there are still alive
   */
  public static boolean isLiving(
      ChunkContext context,
      CorruptionProfile profile,
      int x,
      int z
  ) {
    return context.blightAt(x, z) < profile.livingGroveThreshold();
  }

  /**
   * Removes every leaf outside a living grove.
   *
   * <p>Whole-canopy removal rather than a per-leaf roll, so there are no half-eaten crowns and no
   * orphaned leaves left floating where a trunk used to be.
   */
  private static void stripDeadCanopy(
      ChunkContext context,
      CorruptionProfile profile,
      TreeScan scan
  ) {
    for (BlockPosition leaf : scan.leaves()) {
      if (isLiving(context, profile, leaf.x(), leaf.z())) {
        continue;
      }

      context.set(leaf.x(), leaf.y(), leaf.z(), Material.AIR);
    }
  }

  /**
   * Turns a trunk into a charred standing snag, shortened and stubbed.
   */
  private static void raiseSnag(
      ChunkContext context,
      AshPalette palette,
      CorruptionProfile profile,
      Tree tree
  ) {
    int height = trunkHeight(context, tree);
    if (height <= 0) {
      return;
    }

    boolean snapped = context.chance(profile.snapShare());
    int keep = snapped ? context.between(2, Math.max(2, height / 2)) : height;

    for (int i = 0; i < height; i++) {
      int y = tree.y() + i;
      if (!Tag.LOGS.isTagged(context.typeAt(tree.x(), y, tree.z()))) {
        continue;
      }

      if (i < keep) {
        context.set(tree.x(), y, tree.z(), CharredWood.standing(palette));
      } else {
        context.set(tree.x(), y, tree.z(), Material.AIR);
      }
    }

    if (!snapped) {
      growBranchStubs(context, palette, tree, keep);
    }
  }

  /**
   * Puts a couple of bare branches near the top, so the snag is not a plain post.
   */
  private static void growBranchStubs(
      ChunkContext context,
      AshPalette palette,
      Tree tree,
      int height
  ) {
    if (height < 4) {
      return;
    }

    int branches = context.between(1, 3);

    for (int i = 0; i < branches; i++) {
      int y = tree.y() + height - 1 - context.between(0, 2);
      boolean alongX = context.nextBoolean();
      int direction = context.nextBoolean() ? 1 : -1;
      int length = context.between(1, BRANCH_LENGTH);

      for (int step = 1; step <= length; step++) {
        int x = alongX ? tree.x() + direction * step : tree.x();
        int z = alongX ? tree.z() : tree.z() + direction * step;

        if (!context.inChunk(x, z) || context.typeAt(x, y, z) != Material.AIR) {
          break;
        }

        context.set(x, y, z, CharredWood.fallen(palette, alongX ? Axis.X : Axis.Z));
      }
    }
  }

  /**
   * Brings a trunk down, leaving a stump and the trunk lying in the ash.
   */
  private static void collapse(ChunkContext context, AshPalette palette, Tree tree) {
    int height = trunkHeight(context, tree);
    if (height <= 0) {
      return;
    }

    for (int i = 0; i < height; i++) {
      int y = tree.y() + i;
      if (!Tag.LOGS.isTagged(context.typeAt(tree.x(), y, tree.z()))) {
        continue;
      }

      // Leave a short stump so it is clear the tree stood here
      if (i == 0) {
        context.set(tree.x(), y, tree.z(), CharredWood.standing(palette));
      } else {
        context.set(tree.x(), y, tree.z(), Material.AIR);
      }
    }

    layTrunk(context, palette, tree, Math.min(height, 7));
  }

  private static void layTrunk(
      ChunkContext context,
      AshPalette palette,
      Tree tree,
      int length
  ) {
    boolean alongX = context.nextBoolean();
    int direction = context.nextBoolean() ? 1 : -1;

    for (int step = 1; step <= length; step++) {
      int x = alongX ? tree.x() + direction * step : tree.x();
      int z = alongX ? tree.z() : tree.z() + direction * step;

      if (!context.inChunk(x, z)) {
        return;
      }

      int groundY = context.groundY(x, z);
      if (context.typeAt(x, groundY + 1, z) != Material.AIR) {
        continue;
      }

      context.set(x, groundY + 1, z, CharredWood.fallen(palette, alongX ? Axis.X : Axis.Z));
    }
  }

  private static int trunkHeight(ChunkContext context, Tree tree) {
    int height = 0;

    while (height < CROWN_HEIGHT
        && Tag.LOGS.isTagged(context.typeAt(tree.x(), tree.y() + height, tree.z()))) {
      height++;
    }

    return height;
  }
}
