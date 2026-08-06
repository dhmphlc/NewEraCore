package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ash.AshPalette;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Clears the undergrowth that could not survive the ashfall.
 *
 * <p>Survival is decided per <em>grove</em>, not per plant: inside a living grove the undergrowth
 * is
 * left completely alone, and outside one it is gone. Rolling for each plant separately is what left
 * the ground looking picked at, with single blades of grass standing in an ash field.
 *
 * <p>Nothing is simply deleted where it can be helped. Cleared plants become dead bushes often
 * enough
 * that the ground still has something on it, because bare swept ground reads as a deletion rather
 * than
 * as a dead landscape.
 */
public final class DeadUndergrowth {

  private static final Set<Material> UNDERGROWTH = Set.of(
      Material.SHORT_GRASS,
      Material.TALL_GRASS,
      Material.FERN,
      Material.LARGE_FERN,
      Material.SWEET_BERRY_BUSH,
      Material.SUGAR_CANE,
      Material.VINE,
      Material.LILY_PAD
  );

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
    if (DeadTrees.isLiving(context, profile, x, z)) {
      return;
    }

    int floor = context.scanFloor(x, z);

    for (int y = context.surfaceY(x, z) + 1; y >= floor; y--) {
      Material material = context.typeAt(x, y, z);

      if (!isFragile(material)) {
        continue;
      }

      boolean standing = context.typeAt(x, y - 1, z).isSolid();
      Material replacement = standing && context.chance(profile.deadBushChance())
          ? palette.litter()
          : Material.AIR;

      context.set(x, y, z, replacement);
    }
  }

  private static boolean isFragile(Material material) {
    return UNDERGROWTH.contains(material)
        || Tag.FLOWERS.isTagged(material)
        || Tag.SMALL_FLOWERS.isTagged(material)
        || Tag.SAPLINGS.isTagged(material)
        || Tag.CROPS.isTagged(material);
  }
}
