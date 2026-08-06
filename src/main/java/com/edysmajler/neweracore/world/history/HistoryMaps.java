package com.edysmajler.neweracore.world.history;

import com.edysmajler.neweracore.config.HistoryConfig;
import java.util.List;

/**
 * Every history layer for one world, built once.
 *
 * <p>Building a map calibrates its distribution against thousands of samples, which is worth doing
 * once per world and absurd to do per chunk. The maps only depend on the world seed, so one
 * instance serves every chunk, every later system, and every query about a place the player has not
 * reached.
 *
 * <p>This class is the registry: a new layer is added here, and {@link #all()} then carries it into
 * debug output without anything else being told about it.
 */
public final class HistoryMaps {

  private final WarMap war;
  private final AshfallMap ashfall;
  private final RestorationMap restoration;
  private final List<HistoryMap> all;

  /**
   * Builds the layers for one world.
   *
   * @param worldSeed the world seed
   * @param config the history settings
   */
  public HistoryMaps(long worldSeed, HistoryConfig config) {
    this.war = new WarMap(worldSeed, config);
    this.ashfall = new AshfallMap(worldSeed, config);
    this.restoration = new RestorationMap(worldSeed, config);
    this.all = List.of(war, ashfall, restoration);
  }

  /**
   * Returns the layer describing how hard the fighting was.
   *
   * @return the war map
   */
  public WarMap war() {
    return war;
  }

  /**
   * Returns the layer describing how much ash settled.
   *
   * @return the ashfall map
   */
  public AshfallMap ashfall() {
    return ashfall;
  }

  /**
   * Returns the layer describing how much life held on.
   *
   * @return the restoration map
   */
  public RestorationMap restoration() {
    return restoration;
  }

  /**
   * Returns every layer, in registration order.
   *
   * @return the maps
   */
  public List<HistoryMap> all() {
    return all;
  }
}
