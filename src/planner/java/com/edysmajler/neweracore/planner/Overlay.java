package com.edysmajler.neweracore.planner;

/**
 * A way of looking at the same world.
 *
 * <p>Separate toggles rather than one "mode" selector, because the questions a designer asks are
 * combinations: is this valley flat <em>and</em> near water <em>and</em> inside a devastated band.
 * A mode selector answers one at a time and makes you hold the other two in your head.
 */
public enum Overlay {

  /** Tints the ground by the kind of country it is. */
  TERRAIN("Biomes"),

  /** Paints rivers and sea by depth. */
  WATER("Water"),

  /** Heats the map by the corruption field, which decides each chunk's damage level. */
  CORRUPTION("Corruption"),

  /** Heats the map by the impact field, where craters cluster. */
  IMPACT("Impacts"),

  /** Draws the structures, towns and craters the seed already commits to. */
  SITES("Seeded sites"),

  /** Draws a coordinate grid. */
  GRID("Grid");

  private final String label;

  Overlay(String label) {
    this.label = label;
  }

  /**
   * Returns the name to show on the toggle.
   *
   * @return the label
   */
  public String label() {
    return label;
  }

  /**
   * Returns whether this overlay paints pixels rather than vectors.
   *
   * <p>The distinction decides what has to be redrawn when it changes: a raster overlay means
   * rebuilding the map image, while a vector one is drawn fresh every frame anyway.
   *
   * @return true when toggling this requires re-rendering the raster
   */
  public boolean isRaster() {
    return this == TERRAIN || this == WATER || this == CORRUPTION || this == IMPACT;
  }
}
