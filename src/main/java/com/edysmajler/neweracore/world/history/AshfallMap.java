package com.edysmajler.neweracore.world.history;

import com.edysmajler.neweracore.config.HistoryConfig;
import com.edysmajler.neweracore.world.noise.NoiseField;

/**
 * How much ash settled here after the sky closed.
 *
 * <p>Where war destroys, ashfall <em>buries</em>. High ashfall deepens the mantle, drifts it into
 * every hollow, and dries out what water is left; it does not knock anything down. That separation
 * is what makes two devastated regions read differently: a fallout plain is smooth, pale, and
 * quiet, while a battlefield is broken.
 *
 * <p>Deliberately a different width from the war map, so the two do not rise and fall together —
 * the overlap of the two is what produces the worst land in the world, and it has to be uncommon.
 */
public final class AshfallMap implements HistoryMap {

  private static final long SALT = 0x11L;

  private final NoiseField field;

  /**
   * Builds the map for one world.
   *
   * @param worldSeed the world seed
   * @param config the history settings
   */
  public AshfallMap(long worldSeed, HistoryConfig config) {
    this.field = new NoiseField(worldSeed, SALT, config.getAshfallScale(), config.getOctaves());
  }

  @Override
  public String name() {
    return "ashfall";
  }

  @Override
  public double at(int blockX, int blockZ) {
    return field.sample(blockX, blockZ);
  }
}
