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
 * left completely alone — grass included — and outside one it is gone. Rolling for each plant
 * separately is what left the ground looking picked at, with single blades of grass standing in an
 * ash field.
 *
 * <p>Whether a grove keeps its <em>grass</em> depends on the biome group. In dense forest the
 * grove floor stays completely green — a living stand over a swept-bare floor read as its own
 * artefact — but in open country the grove test marks plain meadow with no canopy to explain the
 * survival, and with green floors on everywhere the plains came out half-vanilla. So forest groves
 * keep everything; open-country groves keep their plants but lose their grass; outside a grove,
 * grass is removed from every column in the world.
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
   * @param greenGroves whether living groves here keep their floor untouched, grass included
   */
  public static void applyToColumn(
      ChunkContext context,
      AshPalette palette,
      int x,
      int z,
      boolean greenGroves
  ) {
    CorruptionProfile profile = context.profile();
    boolean grove = context.isLivingGrove(x, z);

    // In dense forest the grove keeps everything, grass included: the fire never reached in here,
    // and a living canopy over a swept floor is half of each treatment — worse than either
    if (grove && greenGroves) {
      return;
    }

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
        // An open-country grove keeps its plants but not its grass: with no dense canopy above
        // to explain the survival, green blades in an ash field read as an edit, not an island
        if (Vegetation.isGrass(material)) {
          context.clear(x, y, z);
        }
        continue;
      }

      // Leaf litter is cleared outright, never converted: a standing bush where a flat leaf lay
      // overstates what was there, and litter comes in patches dense enough that converting a
      // share of each one would carpet the forest floor in bushes.
      boolean standing = material != Material.LEAF_LITTER
          && context.typeAt(x, y - 1, z).isSolid();
      if (standing && context.chance(profile.deadBushChance())) {
        context.set(x, y, z, palette.litter());
      } else {
        context.clear(x, y, z);
      }
    }
  }
}
