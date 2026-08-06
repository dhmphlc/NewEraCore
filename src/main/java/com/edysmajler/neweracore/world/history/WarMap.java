package com.edysmajler.neweracore.world.history;

import com.edysmajler.neweracore.config.HistoryConfig;
import com.edysmajler.neweracore.world.noise.NoiseField;

/**
 * How hard the fighting was here.
 *
 * <p>This is the map that decides where things were <em>destroyed</em> rather than merely buried:
 * craters cluster where it is high, deadfall is snapped and flattened rather than left standing,
 * and the military landmarks belong to it. Huge impacts are sited from it too, so the biggest hole
 * in the ground and the ruined country around it are the same event rather than two coincidences.
 *
 * <p>The widest of the history layers, because a front line is the largest thing that happened.
 */
public final class WarMap implements HistoryMap {

  private static final long SALT = 0x10L;

  private final NoiseField field;

  /**
   * Builds the map for one world.
   *
   * @param worldSeed the world seed
   * @param config the history settings
   */
  public WarMap(long worldSeed, HistoryConfig config) {
    this.field = new NoiseField(worldSeed, SALT, config.getWarScale(), config.getOctaves());
  }

  @Override
  public String name() {
    return "war";
  }

  @Override
  public double at(int blockX, int blockZ) {
    return field.sample(blockX, blockZ);
  }
}
