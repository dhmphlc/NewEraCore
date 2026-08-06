package com.edysmajler.neweracore.world.history;

import com.edysmajler.neweracore.config.HistoryConfig;
import com.edysmajler.neweracore.world.noise.NoiseField;

/**
 * How much life held on, or came back.
 *
 * <p>This map is what keeps the world from being uniformly grim, and it is <strong>not</strong> the
 * inverse of {@link WarMap}. Made an inverse, it would carry no information of its own: every
 * untouched place would simply be somewhere the war was not, and the world would have exactly one
 * axis. Sampled independently, a place can be both heavily shelled and stubbornly green, which is a
 * far more interesting thing to walk into than either alone.
 *
 * <p><strong>Pockets are the point.</strong> On top of the broad layer sits a small, rare, sharply
 * bounded second field that lifts restoration hard wherever it fires. That is the mechanism behind
 * the contrast this world is supposed to be built on — a surviving grove in a burned forest, a
 * green hollow inside a crater field — and it is a mechanism rather than a hope, because leaving
 * contrast to chance is exactly how procedural worlds end up monotonous. Broad noise alone cannot
 * do it: a layer wide enough to define regions is far too wide to put a small living island inside
 * one.
 *
 * <p>The pocket ramps in smoothly from its threshold, so its edge is a fringe rather than a cliff.
 */
public final class RestorationMap implements HistoryMap {

  private static final long SALT = 0x12L;
  private static final long POCKET_SALT = 0x13L;

  private final NoiseField broad;
  private final NoiseField pocket;
  private final double pocketThreshold;
  private final double pocketStrength;

  /**
   * Builds the map for one world.
   *
   * @param worldSeed the world seed
   * @param config the history settings
   */
  public RestorationMap(long worldSeed, HistoryConfig config) {
    this.broad = new NoiseField(
        worldSeed, SALT, config.getRestorationScale(), config.getOctaves());
    // One octave: a pocket wants a clean, compact shape, and extra octaves would only fray it
    this.pocket = new NoiseField(worldSeed, POCKET_SALT, config.getRestorationPocketScale(), 1);
    this.pocketThreshold = config.getRestorationPocketThreshold();
    this.pocketStrength = config.getRestorationPocketStrength();
  }

  @Override
  public String name() {
    return "restoration";
  }

  @Override
  public double at(int blockX, int blockZ) {
    double value = broad.sample(blockX, blockZ);
    double pocketValue = pocket.sample(blockX, blockZ);

    if (pocketValue <= pocketThreshold || pocketThreshold >= 1.0) {
      return value;
    }

    // Ramp from the threshold to the top of the field, so the pocket has a fringe, not a wall
    double ramp = (pocketValue - pocketThreshold) / (1.0 - pocketThreshold);
    return Math.max(value, pocketStrength * smoothstep(ramp));
  }

  /**
   * Samples only the broad layer, without any pocket.
   *
   * <p>Exposed so tests can show that the pockets, not the broad field, are what put green inside a
   * war zone.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the value between 0 and 1
   */
  public double broadAt(int blockX, int blockZ) {
    return broad.sample(blockX, blockZ);
  }

  private static double smoothstep(double t) {
    double clamped = Math.max(0.0, Math.min(1.0, t));
    return clamped * clamped * (3.0 - 2.0 * clamped);
  }
}
