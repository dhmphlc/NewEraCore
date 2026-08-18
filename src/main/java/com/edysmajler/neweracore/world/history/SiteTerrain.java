package com.edysmajler.neweracore.world.history;

/**
 * What the ground at a place is like, as far as siting needs to care.
 *
 * <p>A landmark's <em>story</em> says whether it belongs in this region; this says whether it
 * belongs on this ground. Both are needed, and leaving the second one out produced exactly the
 * artefact it sounds like: a hydroelectric dam on dry flat land in the middle of nowhere, holding
 * nothing back. A place that could not have been built where it stands is worse than no place at
 * all, because it tells the player the world was not thought about.
 *
 * <p>Deliberately tiny, and deliberately declared here rather than in the terrain package.
 * Everything in {@code world/history} is a pure function of the seed and free of Bukkit, which is
 * what makes the whole simulation testable without a server; a seam with two boolean questions
 * keeps that intact while letting the real implementation ask the world generator what it would put
 * here.
 *
 * <p>Both answers default to permissive, so a caller with nothing to say — a test, or a world with
 * no generator to ask — places everything as it did before.
 */
public interface SiteTerrain {

  /** Says yes to everything, for tests and for callers with no terrain to consult. */
  SiteTerrain ANYWHERE = new SiteTerrain() {};

  /**
   * Returns whether there is dry ground here to build on at all.
   *
   * <p>The question that was missing, and it is the one that matters most. Only the places that
   * <em>wanted</em> water were ever asked about the ground, so everything else — a hospital, a
   * radio mast, a missile silo — could be sited in the middle of an ocean and nobody stopped it. A
   * landmark at sea is not a landmark with a problem, it is a landmark that cannot exist.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the site is on land
   */
  default boolean isDryLand(int blockX, int blockZ) {
    return true;
  }

  /**
   * Returns whether there is water close enough to build against.
   *
   * <p>What a dam and a bridge both need, and the only thing that makes either of them mean
   * anything.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when water is within reach of the site
   */
  default boolean isWaterside(int blockX, int blockZ) {
    return true;
  }

  /**
   * Returns whether the ground is open and level enough to build across.
   *
   * <p>What an airport needs. A runway laid through a mountain range can be levelled — the
   * earthworks will do it — but nothing can be done about the ridge an aircraft would have to fly
   * through on approach, so the answer is not to put the airport there.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the ground is open country rather than hills
   */
  default boolean isOpen(int blockX, int blockZ) {
    return true;
  }
}
