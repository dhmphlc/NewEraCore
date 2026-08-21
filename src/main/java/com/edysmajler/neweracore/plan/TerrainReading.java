package com.edysmajler.neweracore.plan;

/**
 * Everything the ground at one position has to say about whether something can be built on it.
 *
 * <p>The numbers a designer actually needs are not the ones the block world stores. A height alone
 * says nothing; a height compared with the land around it says "ridge" or "valley floor", and that
 * is the difference between a good town and a town nobody can walk into. So the reading carries
 * derived measures — slope, relief, enclosure, distance to water — alongside the raw facts.
 *
 * <p>Measured at snapshot resolution, so these are the shape of the country rather than of a
 * particular block. That is the right scale for siting: a designer picking a valley does not care
 * that one column in it is two blocks higher.
 *
 * @param blockX absolute block x the reading was taken at
 * @param blockZ absolute block z the reading was taken at
 * @param height the surface y, water surface included
 * @param waterDepth how deep the water is, 0 on dry land
 * @param terrain the kind of country
 * @param land whether the generator puts land here rather than open water
 * @param rugged whether the biome itself is broken ground
 * @param slope the largest height difference to a neighbouring sample, in blocks
 * @param relief how high this stands over the land immediately around it; negative in hollows
 * @param enclosure the share of surrounding directions where the land rises well above this, 0 to 1
 * @param waterDistance blocks to the nearest water, or -1 when none is within the search
 */
public record TerrainReading(
    int blockX,
    int blockZ,
    int height,
    int waterDepth,
    TerrainClass terrain,
    boolean land,
    boolean rugged,
    int slope,
    int relief,
    double enclosure,
    int waterDistance
) {

  /**
   * Returns whether this position is standing water.
   *
   * @return true when there is water here
   */
  public boolean isWater() {
    return waterDepth > 0 || !land;
  }

  /**
   * Returns whether the ground is flat enough to build across without terracing.
   *
   * @return true on gentle ground
   */
  public boolean isBuildable() {
    return !isWater() && slope <= 3;
  }

  /**
   * Returns how strongly this reads as a valley floor: enclosed, low, and not steep.
   *
   * <p>A single number because that is what a dam or a sheltered settlement is looking for, and
   * because judging it from three separate figures is exactly the kind of eyeballing that puts a
   * reservoir on a hilltop. Derived, not measured: enclosure decides how much of it there is, and
   * standing above the surrounding land removes it entirely.
   *
   * @return the value between 0 and 1
   */
  public double valley() {
    if (relief > 1) {
      return 0.0;
    }

    double depth = Math.clamp(-relief / 6.0, 0.0, 1.0);
    return enclosure * (0.4 + 0.6 * depth);
  }
}
