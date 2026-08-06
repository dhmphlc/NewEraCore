package com.edysmajler.neweracore.world.ash;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.Vegetation;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import com.edysmajler.neweracore.world.terrain.TerrainProbe;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Lays the ashfall over the land.
 *
 * <p>This is the pass that carries the whole look, and it works the way snow does in a winter
 * biome: <strong>almost every surface gets the same treatment</strong>, and only its depth varies.
 * Earlier versions gated ground changes behind a patch threshold, so most columns stayed vanilla
 * and the affected ones looked picked out one by one — the signature of griefing. Universal
 * coverage with varying depth reads as weather instead.
 *
 * <p>Where the ash sits is decided by the shape of the land, not by dice: it piles up in hollows,
 * lies evenly on flat ground, thins on moderate slopes, and is stripped off steep faces down to
 * bare rock. Which pale material appears comes from the detail field, which varies over many
 * blocks, so materials form broad areas rather than per-block confetti.
 */
public final class AshMantle {

  /**
   * Ground materials whose top face is a pixel short of a full square.
   *
   * <p>Nothing can rest on one of these. A snow layer or a moss carpet sitting on a dirt path is
   * unsupported the moment it is written, and because this engine writes with physics disabled it
   * looks fine until any block update reaches it — and then the whole shelf goes at once. That is
   * why breaking one snow layer in a snowy biome took every other layer around it with it.
   *
   * <p>So a column that has to hold something up gets {@link AshPalette#deepAsh()} instead, which
   * is a full block in every palette.
   */
  private static final Set<Material> THIN_TOPPED = Set.of(Material.DIRT_PATH);

  private AshMantle() {}

  /**
   * Applies the mantle to one column.
   *
   * @param context the chunk being transformed
   * @param probe the chunk's terrain facts
   * @param palette the biome's materials
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   */
  public static void applyToColumn(
      ChunkContext context,
      TerrainProbe probe,
      AshPalette palette,
      int x,
      int z
  ) {
    CorruptionProfile profile = context.profile();
    // groundY, not surfaceY: under a canopy the highest block is a leaf, and treating that as the
    // surface is what left every forest floor untouched
    int surfaceY = context.groundY(x, z);
    Material surface = context.typeAt(x, surfaceY, z);

    if (!isMantleable(surface)) {
      return;
    }

    if (context.isReserved(x, z)) {
      // Something was built here. Ash falls on it like everything else, but the surface underneath
      // stays: a highway repaved with ash ground is a highway that was never there.
      layCarpet(context, palette, profile, x, surfaceY, z);
      return;
    }

    if (probe.isScoured(x, z, profile.scourSlope())) {
      scour(context, palette, x, surfaceY, z);
      return;
    }

    settle(context, probe, palette, profile, x, surfaceY, z);
  }

  /**
   * Strips an exposed face back to rock.
   */
  private static void scour(
      ChunkContext context,
      AshPalette palette,
      int x,
      int surfaceY,
      int z
  ) {
    Material rock = context.detailAt(x, z) < 0.7 ? palette.scouredRock() : palette.grit();
    context.set(x, surfaceY, z, rock);
  }

  /**
   * Covers sheltered ground, deeper in hollows.
   */
  private static void settle(
      ChunkContext context,
      TerrainProbe probe,
      AshPalette palette,
      CorruptionProfile profile,
      int x,
      int surfaceY,
      int z
  ) {
    boolean hollow = probe.isHollow(x, z);
    boolean deep = hollow || context.detailAt(x, z) < profile.deepAshShare();

    Material ground = deep ? palette.deepAsh() : palette.ashGround();
    if (THIN_TOPPED.contains(ground) && bearsSomething(context, profile, x, surfaceY, z)) {
      ground = palette.deepAsh();
    }

    context.set(x, surfaceY, z, ground);

    // In hollows the ash has drifted deep enough to raise the ground itself
    int top = surfaceY;
    if (hollow && context.chance(profile.driftChance())
        && context.typeAt(x, surfaceY + 1, z) == Material.AIR) {
      top = surfaceY + 1;
      context.set(x, top, z, palette.deepAsh());
    }

    layCarpet(context, palette, profile, x, top, z);
  }

  /**
   * Returns whether this column is holding something up, or is about to be.
   *
   * <p>Snow that generated here, undergrowth a living grove kept, or the ash carpet this pass is
   * about to lay: any of them needs a full block underneath.
   */
  private static boolean bearsSomething(
      ChunkContext context,
      CorruptionProfile profile,
      int x,
      int surfaceY,
      int z
  ) {
    Material above = context.typeAt(x, surfaceY + 1, z);

    if (above == Material.SNOW || Vegetation.needsFooting(above)) {
      return true;
    }

    // Mirrors layCarpet's own test, so the ground is chosen knowing what will land on it
    return above.isAir() && context.detailAt(x, z) <= profile.ashCarpetCoverage();
  }

  private static void layCarpet(
      ChunkContext context,
      AshPalette palette,
      CorruptionProfile profile,
      int x,
      int groundY,
      int z
  ) {
    if (context.typeAt(x, groundY + 1, z) != Material.AIR) {
      return;
    }

    if (context.detailAt(x, z) > profile.ashCarpetCoverage()) {
      return;
    }

    context.set(x, groundY + 1, z, palette.ashCarpet());
  }

  /**
   * Returns whether a surface can take ash.
   *
   * <p>Water, ice, and lava are left alone here; drying them out is a separate pass with its own
   * rules. Everything solid is fair game, including sand and stone, because ash falls on everything
   * — that universality is the point.
   */
  private static boolean isMantleable(Material material) {
    if (material.isAir() || !material.isSolid()) {
      return false;
    }

    return !ChunkContext.isFluid(material)
        && !Vegetation.isStanding(material)
        && !Tag.LOGS.isTagged(material)
        && !Tag.LEAVES.isTagged(material);
  }
}
