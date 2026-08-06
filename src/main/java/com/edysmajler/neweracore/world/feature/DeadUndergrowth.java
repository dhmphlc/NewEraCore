package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.Vegetation;
import com.edysmajler.neweracore.world.ash.AshPalette;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import org.bukkit.Material;

/**
 * Clears the undergrowth that could not survive the ashfall.
 *
 * <p>Survival is decided per <em>grove</em>, not per plant: inside a living grove the undergrowth
 * is
 * left completely alone, and outside one it is gone. Rolling for each plant separately is what left
 * the ground looking picked at, with single blades of grass standing in an ash field.
 *
 * <p>Grass is the one exception to the grove rule, and it is absolute: short and tall grass are
 * removed from every column in the world, living grove or not. A grove may look alive, but green
 * blades poking out of an ash field are the loudest remaining sign that the ground was edited
 * rather than buried.
 *
 * <p>Nothing is simply deleted where it can be helped. Cleared plants become dead bushes often
 * enough
 * that the ground still has something on it, because bare swept ground reads as a deletion rather
 * than
 * as a dead landscape.
 */
public final class DeadUndergrowth {

  private DeadUndergrowth() {}

  /**
   * Clears one column's undergrowth.
   *
   * @param context the chunk being transformed
   * @param palette the biome's materials
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   */
  public static void applyToColumn(
      ChunkContext context,
      AshPalette palette,
      int x,
      int z
  ) {
    CorruptionProfile profile = context.profile();
    boolean grove = DeadTrees.isLiving(context, profile, x, z);

    int floor = context.scanFloor(x, z);
    // Plants do not count towards the snapshot's surface height, and the tall ones stand more than
    // one block above it. Looking only one block up found a sunflower's stem and left its head
    // floating, which is the artefact that named Vegetation.REACH.
    int ceiling = context.surfaceY(x, z) + Vegetation.REACH;

    for (int y = ceiling; y >= floor; y--) {
      Material material = context.typeAt(x, y, z);

      if (!Vegetation.isFragile(material)) {
        continue;
      }

      if (grove) {
        // The grove keeps everything else, but grass survives nowhere. No litter either: a dead
        // bush dropped into living undergrowth is the odd thing out.
        if (Vegetation.isGrass(material)) {
          context.clear(x, y, z);
        }
        continue;
      }

      boolean standing = context.typeAt(x, y - 1, z).isSolid();
      if (standing && context.chance(profile.deadBushChance())) {
        context.set(x, y, z, palette.litter());
      } else {
        context.clear(x, y, z);
      }
    }
  }
}
