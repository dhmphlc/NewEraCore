package com.edysmajler.neweracore.plan;

import java.util.Locale;

/**
 * The kind of country a position sits in, coarse enough to colour a map with.
 *
 * <p>Derived from the biome's namespaced key rather than from {@code org.bukkit.block.Biome}, for
 * the reason {@link com.edysmajler.neweracore.world.terrain.LandLookup} spells out: naming that
 * enum makes a class impossible to load outside a running server, and the planner is not a server.
 * A key match also picks up whatever variants a future game version adds, at the cost of grouping
 * anything unrecognised as {@link #OTHER}.
 *
 * <p>Deliberately coarse. This exists so a designer can tell forest from desert from open water at
 * a glance while siting a town; the engine keeps its own, finer biome grouping for deciding
 * materials, and the two are not meant to be the same list.
 */
public enum TerrainClass {

  /** Open sea. */
  OCEAN,

  /** A river or its variants. */
  RIVER,

  /** Shoreline and sandy edges. */
  BEACH,

  /** Open country: plains, meadows, fields. */
  PLAINS,

  /** Broadleaf and birch forest. */
  FOREST,

  /** Conifer forest and its groves. */
  TAIGA,

  /** Jungle in all its densities. */
  JUNGLE,

  /** Savanna and its plateaus. */
  SAVANNA,

  /** Desert and badlands. */
  DESERT,

  /** Swamp and mangrove. */
  SWAMP,

  /** Broken ground: hills, windswept slopes. */
  HILLS,

  /** Peaks and mountainsides. */
  MOUNTAIN,

  /** Snowy and icy country. */
  SNOW,

  /** Anything unrecognised, including caves and the other dimensions. */
  OTHER;

  /**
   * Classifies a biome from its key.
   *
   * <p>Order matters: the water and height tests come first because a "frozen_river" is a river
   * before it is snow, and "windswept_forest" is broken ground before it is woodland — the map is
   * there to answer "can I build here", and the harder constraint is the one worth showing.
   *
   * @param biomeKey the biome's key value, such as {@code snowy_taiga}; case is ignored
   * @return the matching class, or {@link #OTHER} when nothing matches
   */
  public static TerrainClass fromBiomeKey(String biomeKey) {
    if (biomeKey == null) {
      return OTHER;
    }

    String name = biomeKey.toLowerCase(Locale.ROOT);

    if (name.contains("ocean")) {
      return OCEAN;
    }
    if (name.contains("river")) {
      return RIVER;
    }
    if (name.contains("peaks") || name.contains("mountain")) {
      return MOUNTAIN;
    }
    if (name.contains("hills") || name.contains("slopes") || name.contains("windswept")) {
      return HILLS;
    }
    if (name.contains("badlands") || name.contains("desert")) {
      return DESERT;
    }
    if (name.contains("beach") || name.contains("shore") || name.contains("stony_shore")) {
      return BEACH;
    }
    if (name.contains("swamp") || name.contains("mangrove")) {
      return SWAMP;
    }
    if (name.contains("jungle") || name.contains("bamboo")) {
      return JUNGLE;
    }
    if (name.contains("taiga") || name.contains("pine") || name.contains("spruce")) {
      return TAIGA;
    }
    if (name.contains("forest") || name.contains("grove") || name.contains("wood")) {
      return FOREST;
    }
    if (name.contains("savanna")) {
      return SAVANNA;
    }
    if (name.contains("snow") || name.contains("ice") || name.contains("frozen")) {
      return SNOW;
    }
    if (name.contains("plains") || name.contains("meadow") || name.contains("field")) {
      return PLAINS;
    }

    return OTHER;
  }

  /**
   * Returns whether this class is standing water rather than ground.
   *
   * @return true for ocean and river
   */
  public boolean isWater() {
    return this == OCEAN || this == RIVER;
  }

  /**
   * Returns the class for a stored ordinal, tolerating a file written by a newer version.
   *
   * @param ordinal the stored ordinal
   * @return the class, or {@link #OTHER} when the ordinal is out of range
   */
  public static TerrainClass byOrdinal(int ordinal) {
    TerrainClass[] all = values();
    return ordinal >= 0 && ordinal < all.length ? all[ordinal] : OTHER;
  }
}
