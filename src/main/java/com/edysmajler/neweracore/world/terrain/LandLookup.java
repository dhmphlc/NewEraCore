package com.edysmajler.neweracore.world.terrain;

import org.bukkit.World;

/**
 * Whether the world generator puts land or open water at a position.
 *
 * <p>Needed because siting a world-scale feature has to know sea from shore <em>before</em>
 * anything is generated. Three quarters of the first huge craters anybody walked to turned out to
 * be at sea, where fluid columns are skipped and so nothing is carved at all: the rarest feature in
 * the world became a kilometre's walk to look at open water.
 *
 * <p>The question asked is the <strong>biome</strong>, not the terrain. Terrain would mean loading
 * the chunk, and every chunk that carves a slice of one crater has to agree about it without
 * loading anything — that constraint is what lets a single bowl cross chunk borders seamlessly.
 * {@code getComputedBiome} asks the generator what it <em>would</em> put there, which is a function
 * of the seed and needs nothing generated, so every chunk gets the same answer for free.
 *
 * <p>This exists as an interface for one specific reason: {@code org.bukkit.block.Biome} cannot be
 * touched outside a running server. Its constants initialise from the server registry, so a class
 * that names it cannot be unit tested — and Mockito cannot even mock it. Keeping the Bukkit call
 * behind this seam leaves the code that <em>decides</em> things testable, and confines the
 * untestable part to {@link #of(World)}.
 */
@FunctionalInterface
public interface LandLookup {

  /** Treats everywhere as land, for tests and for worlds with no generator to ask. */
  LandLookup EVERYWHERE = (blockX, blockZ) -> true;

  /**
   * Returns whether a position is on land rather than out at sea.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the generator puts land here
   */
  boolean isLand(int blockX, int blockZ);

  /**
   * Returns a lookup over a world's generator.
   *
   * <p>Rivers count as water as well as oceans. A crater is fifteen blocks deep, so a river running
   * into one drains straight down it the moment any block update reaches the bank.
   *
   * <p>Matched on the biome's key rather than against {@code Biome} constants, both because the
   * constants are untouchable outside a server and because a key match picks up any ocean or river
   * variant a future game version adds. A world that answers nothing is treated as land: refusing
   * to place anything would be a worse failure than placing it in water.
   *
   * @param world the world to ask
   * @return the lookup
   */
  static LandLookup of(World world) {
    int seaLevel = world.getSeaLevel();

    return (blockX, blockZ) -> {
      String name = world.getComputedBiome(blockX, seaLevel, blockZ).getKey().value();
      return !name.contains("ocean") && !name.contains("river");
    };
  }
}
