package com.edysmajler.neweracore.world.terrain;

import com.edysmajler.neweracore.world.history.SiteTerrain;
import com.edysmajler.neweracore.world.history.TerrainQuery;
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

  /** How far out to look for water when deciding whether a place is on a shore. */
  int WATERSIDE_REACH = 72;

  /** How many points around a site are sampled when looking for water. */
  int WATERSIDE_SAMPLES = 8;

  /**
   * Returns whether a position is on land rather than out at sea.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the generator puts land here
   */
  boolean isLand(int blockX, int blockZ);

  /**
   * Returns whether the ground here is hills rather than open country.
   *
   * <p>Defaults to open, so a caller with nothing to say places everything as before.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the ground is broken enough that nothing wants to be built across it
   */
  default boolean isRugged(int blockX, int blockZ) {
    return false;
  }

  /**
   * Returns what kind of ground is at a point, as the richer terrain seam reads it.
   *
   * <p>Derived from the two questions this interface already answers, so an implementation that
   * only knows land from sea still gives usable ground. {@link #of(World)} overrides it to settle
   * all three from a single biome lookup, because asking twice about the same block costs twice as
   * much and tells you nothing new.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the kind of ground
   */
  default TerrainQuery.Ground ground(int blockX, int blockZ) {
    if (!isLand(blockX, blockZ)) {
      return TerrainQuery.Ground.OCEAN;
    }

    return isRugged(blockX, blockZ) ? TerrainQuery.Ground.RUGGED : TerrainQuery.Ground.OPEN;
  }

  /**
   * Returns this lookup as the area-describing seam.
   *
   * <p>The successor to {@link #siteTerrain()}: the same constraint — nothing generated, and
   * everything a function of the seed — but describing the ground around a site rather than
   * answering three booleans about the point it stands on.
   *
   * @return the terrain query
   */
  default TerrainQuery terrainQuery() {
    return new TerrainQuery() {
      @Override
      public Ground groundAt(int blockX, int blockZ) {
        return ground(blockX, blockZ);
      }
    };
  }

  /**
   * Returns this lookup as the seam the landmark siting reads.
   *
   * <p>Waterside is derived rather than asked for: a ring of points around the site, and if any of
   * them is water then there is something here to dam or to cross. Sampling a ring rather than the
   * centre is the whole point — a dam stands <em>beside</em> the water it holds back, never in it.
   *
   * @return the terrain seam
   */
  default SiteTerrain siteTerrain() {
    return new SiteTerrain() {
      @Override
      public boolean isDryLand(int blockX, int blockZ) {
        return isLand(blockX, blockZ);
      }

      @Override
      public boolean isWaterside(int blockX, int blockZ) {
        for (int i = 0; i < WATERSIDE_SAMPLES; i++) {
          double angle = i * 2.0 * Math.PI / WATERSIDE_SAMPLES;
          int x = blockX + (int) Math.round(Math.cos(angle) * WATERSIDE_REACH);
          int z = blockZ + (int) Math.round(Math.sin(angle) * WATERSIDE_REACH);

          if (!isLand(x, z)) {
            return true;
          }
        }

        return false;
      }

      @Override
      public boolean isOpen(int blockX, int blockZ) {
        return !isRugged(blockX, blockZ);
      }
    };
  }

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

    return new LandLookup() {
      @Override
      public boolean isLand(int blockX, int blockZ) {
        return !isWaterName(nameAt(blockX, blockZ));
      }

      @Override
      public TerrainQuery.Ground ground(int blockX, int blockZ) {
        // One lookup settles all three questions. The categories are exclusive because a biome key
        // is one string: Mojang does not name anything both a river and a hill.
        String name = nameAt(blockX, blockZ);

        if (name.contains("river")) {
          return TerrainQuery.Ground.RIVER;
        }

        if (name.contains("ocean")) {
          return TerrainQuery.Ground.OCEAN;
        }

        return isRuggedName(name) ? TerrainQuery.Ground.RUGGED : TerrainQuery.Ground.OPEN;
      }

      @Override
      public boolean isRugged(int blockX, int blockZ) {
        return isRuggedName(nameAt(blockX, blockZ));
      }

      private boolean isWaterName(String name) {
        return name.contains("ocean") || name.contains("river");
      }

      // Read off the biome's own name. Mojang names the broken ground exactly what it is, so this
      // catches every peak, hill and slope including any a future version adds — and it costs one
      // question of the generator rather than a chunk load.
      private boolean isRuggedName(String name) {
        return name.contains("peaks")
            || name.contains("hills")
            || name.contains("slopes")
            || name.contains("windswept")
            || name.contains("badlands")
            || name.contains("mountain");
      }

      private String nameAt(int blockX, int blockZ) {
        return world.getComputedBiome(blockX, seaLevel, blockZ).getKey().value();
      }
    };
  }
}
