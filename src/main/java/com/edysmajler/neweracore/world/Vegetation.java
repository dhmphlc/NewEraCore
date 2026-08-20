package com.edysmajler.neweracore.world;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * What counts as a plant, which plants the ashfall kills, and which cannot stand on nothing.
 *
 * <p>Every fact below produced a visible artefact in a shipped version, so they live here together
 * rather than in whichever pass tripped over one of them first.
 *
 * <p><strong>A plant can be two blocks tall.</strong> A sunflower, a lilac, tall grass, and a large
 * fern are each one plant built from a lower and an upper block of the <em>same</em> material, and
 * the chunk snapshot's surface height does not count either of them — it reports the highest block
 * that blocks motion, and nothing you can walk through does. A pass that looked one block above
 * that height found the stem and never the head, so sunflowers lost their stems and kept their
 * heads, hanging in the air. {@link #REACH} is how far up a pass has to look to see a whole plant.
 *
 * <p><strong>Grass does not belong in this world at all.</strong> Not a blade, not in a living
 * grove, not anywhere: green tufts poking through an ash field are the single loudest surviving
 * sign that the ground was edited rather than buried.
 *
 * <p><strong>Nothing falls on its own here.</strong> Every write this engine makes has physics
 * disabled, so a plant whose footing is carved away simply hangs there. Anything that removes a
 * block has to take the plant standing on it too, which is what {@link #needsFooting} is for. The
 * same applies the other way up: a vine holds on to what is beside or above it, and outlives it
 * just as quietly once the canopy it hung from is gone — {@link #isHanging} names those.
 *
 * <p><strong>Some plants are solid blocks.</strong> Bamboo, a cactus, and the cap of a huge
 * mushroom all report as solid, so the search for the ground stopped at the <em>top of the
 * plant</em> and called it the floor. The ashfall then landed on top of a bamboo stalk and turned
 * half a mushroom into dirt. No amount of tuning the ashfall would have fixed that, because the
 * ashfall was doing exactly what it was told; the ground underneath it was wrong. {@link
 * #isStanding} names them, so the ground search walks past them and no pass writes into one.
 */
public final class Vegetation {

  /**
   * How far above the snapshot surface height a plant can still reach.
   *
   * <p>Four, because sugar cane grows that tall and none of it counts towards the surface height.
   */
  public static final int REACH = 4;

  /**
   * Ground plants the ashfall kills.
   *
   * <p>Leaf litter belongs here even though it already looks dead: it is leaves, and outside a
   * living grove every leaf goes — the canopy rule, applied to the forest floor. Leaving it out of
   * this set had a second cost: nothing knew it needed footing, so the mantle repaved the ground
   * under it with a dirt path and left the litter floating on a block that cannot hold it. The
   * newer ground plants — bushes, dry grasses, wildflowers — are named for the same reason: none
   * of them sit in the block tags this class otherwise trusts, so an unlisted one is invisible to
   * every pass and ends up perched on repaved ground.
   */
  private static final Set<Material> UNDERGROWTH = Set.of(
      Material.SHORT_GRASS,
      Material.TALL_GRASS,
      Material.FERN,
      Material.LARGE_FERN,
      Material.SWEET_BERRY_BUSH,
      Material.SUGAR_CANE,
      Material.VINE,
      Material.LILY_PAD,
      Material.LEAF_LITTER,
      Material.BUSH,
      Material.FIREFLY_BUSH,
      Material.SHORT_DRY_GRASS,
      Material.TALL_DRY_GRASS,
      Material.WILDFLOWERS
  );

  /**
   * The two-block flowers.
   *
   * <p>Named outright rather than trusted to a block tag, because an upper half left standing on
   * nothing is exactly the artefact this class exists to prevent, and that is too specific a bug to
   * leave resting on what a tag happens to contain in some future game version.
   */
  private static final Set<Material> TALL_FLOWERS = Set.of(
      Material.SUNFLOWER,
      Material.LILAC,
      Material.PEONY,
      Material.ROSE_BUSH,
      Material.PITCHER_PLANT
  );

  /** The grasses, which survive nowhere. */
  private static final Set<Material> GRASS = Set.of(
      Material.SHORT_GRASS,
      Material.TALL_GRASS
  );

  /**
   * Plants that are solid blocks, and are therefore mistaken for ground.
   *
   * <p>Left whole by every pass. A bamboo thicket or a huge mushroom standing untouched in an ash
   * field is a far better sight than one with its top replaced by dirt, and the engine has no
   * business editing a plant it cannot rebuild into a convincing dead shape.
   */
  private static final Set<Material> STANDING = Set.of(
      Material.BAMBOO,
      Material.BAMBOO_SAPLING,
      Material.CACTUS,
      Material.CACTUS_FLOWER,
      Material.BROWN_MUSHROOM_BLOCK,
      Material.RED_MUSHROOM_BLOCK,
      Material.MUSHROOM_STEM,
      Material.BIG_DRIPLEAF,
      Material.BIG_DRIPLEAF_STEM,
      Material.PUMPKIN,
      Material.MELON
  );

  /**
   * Plants that hold on to something beside or above them rather than standing on the ground.
   *
   * <p>Vines are the reason this set exists: they hang off a canopy, and stripping the canopy left
   * them hanging in the air with nothing above them at all.
   */
  private static final Set<Material> HANGING = Set.of(
      Material.VINE,
      Material.GLOW_LICHEN,
      Material.PALE_HANGING_MOSS,
      Material.HANGING_ROOTS,
      Material.SPORE_BLOSSOM,
      Material.CAVE_VINES,
      Material.CAVE_VINES_PLANT
  );

  private Vegetation() {}

  /**
   * Returns whether a material is a plant too fragile to live through an ashfall.
   *
   * @param material the material to test
   * @return true when the ashfall kills it
   */
  public static boolean isFragile(Material material) {
    return UNDERGROWTH.contains(material)
        || TALL_FLOWERS.contains(material)
        || Tag.FLOWERS.isTagged(material)
        || Tag.SMALL_FLOWERS.isTagged(material)
        || Tag.SAPLINGS.isTagged(material)
        || Tag.CROPS.isTagged(material);
  }

  /**
   * Returns whether a material is grass, which is removed everywhere regardless of any survival.
   *
   * @param material the material to test
   * @return true for short and tall grass
   */
  public static boolean isGrass(Material material) {
    return GRASS.contains(material);
  }

  /**
   * Returns whether a plant would be left standing on nothing if the block below it went away.
   *
   * <p>Vines are excluded: they hang off the side of a block, so what is underneath them was never
   * holding them up. Dead bushes and the pale moss ash carpet are included even though nothing in
   * this engine kills them — the engine <em>places</em> them as litter, and a crater or a crash
   * trench carving through the ground afterwards has to take them with it. Leaving the carpet out
   * was a shipped artefact: every structure arrived ringed by ash carpets hanging over its scars.
   *
   * @param material the material to test
   * @return true when the material needs the block beneath it
   */
  public static boolean needsFooting(Material material) {
    return material == Material.DEAD_BUSH
        || material == Material.PALE_MOSS_CARPET
        || (isFragile(material) && !isHanging(material));
  }

  /**
   * Returns whether a material is a plant that happens to be a solid block.
   *
   * <p>Such a plant is not ground, however much it looks like it to a height search, and is never
   * written into.
   *
   * @param material the material to test
   * @return true when the material is a standing solid plant
   */
  public static boolean isStanding(Material material) {
    return STANDING.contains(material);
  }

  /**
   * Returns whether a material is a plant that hangs from what is beside or above it.
   *
   * @param material the material to test
   * @return true when the material needs an anchor rather than a footing
   */
  public static boolean isHanging(Material material) {
    return HANGING.contains(material);
  }
}
