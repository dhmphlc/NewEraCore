package com.edysmajler.neweracore.world.history;

/**
 * What the ground around one site is like, measured once and then asked many questions.
 *
 * <p>The reason this is a record rather than a set of methods on a query: siting asks the same
 * ground several questions in a row — is it dry, is there water, is it open — and the previous
 * design answered each one by going back to the world generator. Dry land alone was asked once per
 * candidate type, so seven identical questions were put to the generator about the same block. A
 * site is sampled once here, and every question after that is arithmetic.
 *
 * <p>The values are shares of sampled points rather than measurements, because measurements are not
 * available. Paper exposes no generator-side height query — {@code getHighestBlockYAt} generates
 * the chunk, and {@code ChunkGenerator} is an interface to implement rather than a way to ask what
 * vanilla would do — so there is no honest way to report a height, a slope, or an elevation
 * difference from a seed alone. What there is, for free, is the computed biome at any point, and a
 * spread of those over an area says a surprising amount about the landform. Nothing here pretends
 * to be more precise than that.
 *
 * @param dryLand whether there is ground here to build on at all
 * @param water share of nearby points that are ocean or river
 * @param river share of nearby points that are river rather than open sea
 * @param relief share of surrounding points that are broken ground
 * @param enclosure share of the outer ring that is high ground, so a low place can know it is held
 */
public record SiteGround(
    boolean dryLand,
    double water,
    double river,
    double relief,
    double enclosure
) {

  /**
   * Ground that suits everything, for tests and for callers with no generator to consult.
   *
   * <p>Permissive on purpose, and it has to be stated rather than defaulted: a record of zeroes
   * would read as a dry inland plain and quietly refuse every site that wants water. A caller with
   * nothing to say should place what it always placed.
   */
  public static final SiteGround ANYWHERE = new SiteGround(true, 1.0, 1.0, 0.0, 1.0);

  /** Open water: nothing can stand here, and there is no point measuring the rest. */
  public static final SiteGround SEA = new SiteGround(false, 1.0, 0.0, 0.0, 0.0);

  /**
   * Returns how open the ground is, as the complement of its relief.
   *
   * <p>What a runway wants. The earthworks can level whatever it lands on, but the ridge an
   * aircraft would have to fly through on approach cannot be levelled, so the answer is not to put
   * the airport there.
   *
   * @return 0 for broken ground, 1 for open country
   */
  public double openness() {
    return 1.0 - relief;
  }

  /**
   * Returns how much this place is a valley with a river in it.
   *
   * <p>The measure the old three-boolean seam could not express, and the reason for this class. Its
   * waterside test meant <em>a river or ocean biome somewhere within 72 blocks</em>, which is also
   * true of a flat coastal plain and of a river wandering across open grassland. Neither is
   * something you could dam. A dam needs water to hold back <em>and</em> high ground on both sides
   * to hold it between, so the two are multiplied: a river with nothing around it scores nothing,
   * and so does a ring of hills with no water in it.
   *
   * @return 0 where there is nothing to dam, approaching 1 in a river valley
   */
  public double valley() {
    return river * enclosure;
  }

  /**
   * Returns whether there is any water within reach at all.
   *
   * <p>The blunt question, kept because crossing water and standing beside it are still real
   * requirements even where damming it is not.
   *
   * @return true when any sampled point nearby is water
   */
  public boolean isWaterside() {
    return water > 0.0;
  }
}
