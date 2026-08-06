package com.edysmajler.neweracore.world.infrastructure;

import com.edysmajler.neweracore.config.InfrastructureConfig;
import com.edysmajler.neweracore.world.ChunkContext;
import org.bukkit.Material;

/**
 * Lays one column of a route: surface, headroom, and a bridge where there is water.
 *
 * <p>Every route hugs the ground it crosses. That is not a shortcut, it is the only rule that
 * survives the constraint this engine is built around: a column's own ground height is something
 * the chunk can see, and neighbouring columns differ by a block at most, so a road drawn column by
 * column comes out continuous even though no chunk ever saw the next one. An engineered grade — the
 * embankments and cuttings of a real motorway — would need to know the terrain kilometres ahead,
 * which is precisely what cannot be known here.
 *
 * <p><strong>Water is the exception, and it is what makes bridges work.</strong> The surface of a
 * body of water is flat, and every column of it reports the same height, so a deck laid two blocks
 * above that surface is at the same height for every chunk that touches the crossing. A bridge is
 * the one piece of engineered grade available for free.
 *
 * <p>The materials are deliberately man-made — tiles, bricks, and gravel rather than stone —
 * because the point of infrastructure is to be legible as something people built. The ash still
 * falls on it: the route reserves its columns so the mantle does not repave them, then dusts
 * itself, so a highway reads as buried rather than swept.
 */
public final class Roadbed {

  /** Surface materials, chosen by the detail field so wear forms patches rather than speckle. */
  private static final Material HIGHWAY_SURFACE = Material.DEEPSLATE_TILES;
  private static final Material HIGHWAY_WORN = Material.CRACKED_DEEPSLATE_TILES;
  private static final Material HIGHWAY_SHOULDER = Material.GRAVEL;
  private static final Material RAIL_BED = Material.GRAVEL;
  private static final Material BRIDGE_DECK = Material.POLISHED_DEEPSLATE;
  private static final Material BRIDGE_PILLAR = Material.DEEPSLATE_BRICKS;

  /** How far above the water a bridge deck runs. */
  private static final int DECK_CLEARANCE = 2;

  private Roadbed() {}

  /**
   * Builds one column of a route.
   *
   * @param context the chunk being transformed
   * @param config the infrastructure settings
   * @param type what kind of route this is
   * @param edge whether the column is at the route's edge rather than its middle
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   */
  public static void build(
      ChunkContext context,
      InfrastructureConfig config,
      RouteType type,
      boolean edge,
      int x,
      int z
  ) {
    int groundY = context.groundY(x, z);

    if (context.isFluidColumn(x, z)) {
      buildBridge(context, config, type, x, groundY, z);
      return;
    }

    clearAbove(context, config, x, groundY, z);

    if (!type.isPaved()) {
      // A power line owns no ground: it only needs the trees out of the way under the wires
      return;
    }

    if (context.detailAt(x, z) < config.getDecay()) {
      // Broken up and grown over. Left as it is rather than paved, so the road comes and goes.
      return;
    }

    context.set(x, groundY, z, surfaceFor(context, type, edge, x, z));
  }

  /**
   * Returns whether a column needs the ash mantle to keep off it.
   *
   * @param type the route type
   * @return true when the route owns the ground here
   */
  public static boolean claimsGround(RouteType type) {
    return type.isPaved();
  }

  /**
   * Carries the deck across water and drops a pillar into it now and then.
   */
  private static void buildBridge(
      ChunkContext context,
      InfrastructureConfig config,
      RouteType type,
      int x,
      int waterY,
      int z
  ) {
    int deckY = waterY + DECK_CLEARANCE;

    if (!type.isPaved()) {
      // Wires simply span it. A power line needs no crossing.
      return;
    }

    context.set(x, deckY, z, BRIDGE_DECK);
    clearAbove(context, config, x, deckY, z);

    int blockX = context.blockX(x);
    int blockZ = context.blockZ(z);
    if ((blockX + blockZ) % config.getBridgePillarSpacing() != 0) {
      return;
    }

    // Down through the water to whatever the riverbed is, all of it inside this one column
    for (int y = deckY - 1; y > context.getMinHeight(); y--) {
      Material material = context.typeAt(x, y, z);

      if (material.isSolid() && !ChunkContext.isFluid(material)) {
        return;
      }

      context.set(x, y, z, BRIDGE_PILLAR);
    }
  }

  /**
   * Opens the headroom a route needs, and dusts what is left with ash.
   */
  private static void clearAbove(
      ChunkContext context,
      InfrastructureConfig config,
      int x,
      int surfaceY,
      int z
  ) {
    for (int y = surfaceY + 1; y <= surfaceY + config.getClearance(); y++) {
      Material material = context.typeAt(x, y, z);

      if (!material.isAir() && !ChunkContext.isFluid(material)) {
        context.clear(x, y, z);
      }
    }
  }

  /**
   * Returns what this piece of surface is made of.
   */
  private static Material surfaceFor(
      ChunkContext context,
      RouteType type,
      boolean edge,
      int x,
      int z
  ) {
    if (type == RouteType.RAILWAY) {
      return RAIL_BED;
    }

    if (edge) {
      return HIGHWAY_SHOULDER;
    }

    return context.detailAt(x, z) < 0.5 ? HIGHWAY_SURFACE : HIGHWAY_WORN;
  }
}
