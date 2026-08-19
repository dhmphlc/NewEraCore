package com.edysmajler.neweracore.world.structures;

import com.edysmajler.neweracore.world.terrain.LandLookup;

/**
 * One kind of scattered structure: what it is called, how big it is, where it may stand, and how
 * to build it.
 *
 * <p>The two halves of a definition answer two different questions at two different times. The
 * siting half — {@link #radius()}, {@link #weight()}, {@link #suits} — runs while sites are being
 * resolved, before any terrain exists, so it may only consult things that are pure functions of
 * the seed. The building half — {@link #place} — runs once the whole footprint has generated, with
 * the real terrain under it, and is free to read actual ground heights.
 *
 * <p>Keep {@code suits} honest: a structure that ignores its ground tells the player nothing was
 * thought about. A wreck in mid-ocean is worse than no wreck.
 */
public interface StructureDefinition {

  /**
   * Returns the identifier players and configs use for this structure.
   *
   * @return the id, lower case, stable across versions
   */
  String id();

  /**
   * Returns how far from its centre the structure can reach, in blocks, at any rotation.
   *
   * <p>This bounds the footprint: every chunk within this reach takes part in triggering the
   * placement, and the candidate window sites are searched in is derived from the largest radius
   * in the registry. Understating it is the silent failure mode — a chunk near the edge never
   * considers the site and the structure never appears.
   *
   * @return the reach in blocks
   */
  int radius();

  /**
   * Returns this structure's share of the draw when a site picks what stands on it.
   *
   * @return the relative weight, above zero
   */
  double weight();

  /**
   * Returns whether this structure could stand at a position.
   *
   * <p>Asked at siting time, so only the seed-pure {@link LandLookup} is available. The default
   * wants dry land at the centre, which suits almost everything; override to demand more or less.
   *
   * @param land what the world generator puts at a position
   * @param blockX absolute block x of the site centre
   * @param blockZ absolute block z of the site centre
   * @return true when the ground could have held this
   */
  default boolean suits(LandLookup land, int blockX, int blockZ) {
    return land.isLand(blockX, blockZ);
  }

  /**
   * Builds the structure, terrain work included.
   *
   * <p>Runs exactly once per site, after every chunk in the footprint exists. All reads and writes
   * go through the field, which works in absolute world coordinates and keeps physics off.
   *
   * @param field the world writer over the footprint
   * @param site where and which way
   */
  void place(StructureField field, StructureSite site);
}
