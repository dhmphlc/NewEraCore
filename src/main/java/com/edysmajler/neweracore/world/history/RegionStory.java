package com.edysmajler.neweracore.world.history;

import com.edysmajler.neweracore.config.HistoryConfig;

/**
 * What kind of place a region is, in one word.
 *
 * <p>The terrain itself is never driven by this enum — it reads the continuous history values, so
 * no story boundary can ever show up as a seam in the ground, exactly as corruption levels are
 * blended rather than stepped. What the enum is for is the <em>discrete</em> decisions that come
 * next: which ruins to raise, whether a road runs through, what is worth looting, who survived
 * here. Those are choices between distinct things, and they need a name for the place, not a
 * number.
 *
 * <p>Which is the real payoff of the whole history layer. Every future system asks one question —
 * "what happened here?" — and gets the same answer, instead of inventing its own randomness and
 * quietly contradicting everything else in the same valley.
 *
 * <p>The classification is total and ordered: war outranks ash, ash outranks regrowth. A region
 * that was shelled <em>and</em> buried is a battlefield first, because that is what a player
 * standing in it would say it was.
 */
public enum RegionStory {

  /**
   * Shelled and then buried. Crater fields, burned forest, military wreckage.
   */
  FRONT_LINE("shelled and then buried under the fallout"),

  /**
   * Fought over but never buried, so what was destroyed is still legible. Broken towns, roads,
   * scattered wreckage.
   */
  RUINED_TOWNS("fought over, and what was broken here is still recognisable"),

  /**
   * No fighting reached it; the sky did. Deep ash, standing dead trees, near silence.
   */
  ASHEN_WASTE("no fighting reached it, only the ash"),

  /**
   * The war passed it by and life held on. Surviving forest, green hollows.
   */
  GREEN_REFUGE("the war passed it by and something is still alive"),

  /**
   * Untouched by either, and slowly emptied anyway. Abandoned farmland, dry plains, dead villages.
   */
  DUST_BOWL("nothing happened here; everyone left anyway");

  private final String summary;

  RegionStory(String summary) {
    this.summary = summary;
  }

  /**
   * Returns a one-line description, for debug output and future in-game lore.
   *
   * @return the summary
   */
  public String summary() {
    return summary;
  }

  /**
   * Returns whether this story is one the war reached.
   *
   * @return true for the two war stories
   */
  public boolean isWarTorn() {
    return this == FRONT_LINE || this == RUINED_TOWNS;
  }

  /**
   * Classifies a region from its history values.
   *
   * @param config the thresholds
   * @param war how hard the fighting was, 0 to 1
   * @param ashfall how much ash settled, 0 to 1
   * @param restoration how much life held on, 0 to 1
   * @return the story
   */
  public static RegionStory of(
      HistoryConfig config,
      double war,
      double ashfall,
      double restoration
  ) {
    boolean fought = war >= config.getWarHigh();
    boolean buried = ashfall >= config.getAshfallHigh();

    if (fought) {
      return buried ? FRONT_LINE : RUINED_TOWNS;
    }

    if (buried) {
      return ASHEN_WASTE;
    }

    return restoration >= config.getRestorationHigh() ? GREEN_REFUGE : DUST_BOWL;
  }
}
