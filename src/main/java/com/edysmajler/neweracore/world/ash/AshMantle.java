package com.edysmajler.neweracore.world.ash;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import com.edysmajler.neweracore.world.terrain.TerrainProbe;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Lays the ashfall over the land.
 *
 * <p>This is the pass that carries the whole look, and it works the way snow does in a winter
 * biome:
 * <strong>almost every surface gets the same treatment</strong>, and only its depth varies. Earlier
 * versions gated ground changes behind a patch threshold, so most columns stayed vanilla and the
 * affected ones looked picked out one by one — the signature of griefing. Universal coverage with
 * varying depth reads as weather instead.
 *
 * <p>Where the ash sits is decided by the shape of the land, not by dice: it piles up in hollows,
 * lies evenly on flat ground, thins on moderate slopes, and is stripped off steep faces down to
 * bare
 * rock. Which pale material appears comes from the detail field, which varies over many blocks, so
 * materials form broad areas rather than per-block confetti.
 */
public final class AshMantle {

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
   * —
   * that universality is the point.
   */
  private static boolean isMantleable(Material material) {
    if (material.isAir() || !material.isSolid()) {
      return false;
    }

    return material != Material.WATER
        && material != Material.LAVA
        && !Tag.ICE.isTagged(material)
        && !Tag.LOGS.isTagged(material)
        && !Tag.LEAVES.isTagged(material);
  }
}
