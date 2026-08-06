package com.edysmajler.neweracore.world.history;

import com.edysmajler.neweracore.config.HistoryConfig;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;

/**
 * Bends a corruption profile to fit what happened in the region.
 *
 * <p>This is the join between the history layer and everything already built, and the reason it is
 * a <em>transformation of the existing numbers</em> rather than a new set of switches: every pass
 * in the engine already reads a {@link CorruptionProfile}, so shaping that profile makes ash,
 * trees, water, and craters all answer to the region's history without a single branch being added
 * inside any of them. A story that had to be handled pass by pass would be forgotten by the next
 * pass someone writes.
 *
 * <p>The maps push the numbers in the directions their meanings imply, and only in those
 * directions:
 *
 * <ul>
 * <li><strong>Ashfall buries.</strong> Deeper ash, more drift, drier watercourses. It breaks
 * nothing — a fallout plain is smooth and quiet.</li>
 * <li><strong>War breaks.</strong> More craters over more ground, more trees snapped and flattened,
 * fewer groves left standing. It does not deepen the ash.</li>
 * <li><strong>Restoration spares.</strong> More surviving groves, thinner ash, wetter ground. It
 * fights the other two rather than merely being their absence.</li>
 * </ul>
 *
 * <p>Every push is proportional and monotonic, so a region halfway up the war map gets half the
 * treatment and neighbouring regions never step. The influence values are the only strengths, which
 * means setting all three to zero restores the pre-history engine exactly.
 *
 * <p><strong>The one hard floor.</strong> Ash coverage can never fall below {@link #MIN_CARPET}, no
 * matter how green the region. The first principle of this engine is that ash falls on everything
 * and only its depth varies; a green refuge is allowed to be a lighter dusting and is not allowed
 * to be untouched vanilla ground, because a boundary between edited and pristine land is the one
 * thing that always reads as griefing.
 */
public final class HistoryShaping {

  /**
   * The lightest ash coverage any region may end up with.
   *
   * <p>Matches {@link CorruptionProfile#DUSTING}, the calmest profile in the engine.
   */
  public static final double MIN_CARPET = 0.45;

  /** Ceiling on craters per chunk, matching the configuration's own maximum. */
  private static final double MAX_CRATERS = 6.0;

  private HistoryShaping() {}

  /**
   * Returns the profile a region actually runs on.
   *
   * @param base the corruption level's blended profile
   * @param config the influence strengths
   * @param war how hard the fighting was, 0 to 1
   * @param ashfall how much ash settled, 0 to 1
   * @param restoration how much life held on, 0 to 1
   * @return the shaped profile
   */
  public static CorruptionProfile shape(
      CorruptionProfile base,
      HistoryConfig config,
      double war,
      double ashfall,
      double restoration
  ) {
    double buried = ashfall * config.getAshfallInfluence();
    double broken = war * config.getWarInfluence();
    double spared = restoration * config.getRestorationInfluence();

    return new CorruptionProfile(
        Math.max(MIN_CARPET, lower(raise(base.ashCarpetCoverage(), buried), spared * 0.5)),
        lower(raise(base.deepAshShare(), buried), spared),
        raise(base.driftChance(), buried * 0.5),
        base.scourSlope(),
        lower(raise(base.livingGroveThreshold(), spared), broken),
        raise(base.snapShare(), broken * 0.5),
        raise(base.collapseShare(), broken * 0.5),
        base.deadBushChance(),
        lower(raise(base.waterDryingChance(), buried * 0.5), spared),
        impactZone(base.impactZoneThreshold(), war, config.getWarInfluence()),
        craters(base.cratersPerZone(), war, config.getWarInfluence()),
        raise(base.largeCraterShare(), broken * 0.5)
    );
  }

  /**
   * Moves a value towards 1 by a share of the distance left.
   *
   * <p>Proportional rather than additive, so a value already near its ceiling cannot be pushed past
   * it and no clamping is needed anywhere.
   */
  private static double raise(double base, double amount) {
    return base + (1.0 - base) * clamp(amount);
  }

  /**
   * Moves a value towards 0 by a share of itself.
   */
  private static double lower(double base, double amount) {
    return base * (1.0 - clamp(amount));
  }

  /**
   * Decides whether the region has crater fields at all.
   *
   * <p>A gate rather than a nudge, and the only number history is allowed to swing both ways.
   * Whether craters exist is war's decision: a region the fighting never reached must not get
   * bombarded merely because its corruption level came out high, and a region that was fought over
   * must get bombarded even where the ash is thin. Only how thickly they cluster is left to the
   * level.
   *
   * <p>This distinction was not obvious from the code. It took measuring the world to see that
   * crater density alone left the corruption field — which knows nothing about the war — as the
   * real decider of where the bombardment was, which is precisely the incoherence the history layer
   * exists to end.
   *
   * <p>Thresholds are percentiles, so <em>raising</em> this one shrinks the ground craters can
   * reach.
   */
  private static double impactZone(double base, double war, double influence) {
    double swing = (clamp(war) - 0.5) * 2.0 * clamp(influence);
    return swing >= 0.0 ? lower(base, swing) : raise(base, -swing);
  }

  /**
   * Scales crater density with the war map, from well under the base rate to well over it.
   *
   * <p>Craters are the one number that has to move by more than the distance to its ceiling: the
   * difference between a quiet region and a bombarded one is a multiple, not a percentage.
   */
  private static double craters(double base, double war, double influence) {
    double factor = 1.0 + (0.4 + 1.6 * clamp(war) - 1.0) * clamp(influence);
    return Math.min(MAX_CRATERS, base * Math.max(0.0, factor));
  }

  private static double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
