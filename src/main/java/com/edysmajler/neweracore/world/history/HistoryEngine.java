package com.edysmajler.neweracore.world.history;

import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.corruption.CorruptionZone;
import com.edysmajler.neweracore.world.noise.NoiseFields;
import java.util.List;

/**
 * The simulated history of one world, and the only way to ask about it.
 *
 * <p>What this stopped being is a terrain transformer. The engine no longer decides how to damage a
 * chunk; it works out what happened to the region the chunk is in, and the damage follows from
 * that. Ruins, roads, loot, radiation, survivor camps — every one of them can be derived from the
 * same simulated past instead of rolling its own dice, and that shared source is the only way a
 * procedural world ends up internally consistent.
 *
 * <p>One instance per world, cached, because building the layers calibrates each field against
 * thousands of samples. After that, a query is a handful of noise samples and nine integer hashes:
 * cheap enough to call per chunk on the main thread, and cheap enough that a later system can call
 * it per block if it wants to. There is no cache and no mutable state on purpose — a pure function
 * needs neither, and both would be a correctness risk on a server that generates chunks from more
 * than one thread.
 *
 * <p>This class has no dependency on Bukkit, and neither does anything else in this package. The
 * entire history of a world can be sampled, measured, and tested without a server running, which is
 * what makes claims about the world's variety checkable instead of aspirational.
 */
public final class HistoryEngine {

  private final WorldEngineConfig config;
  private final NoiseFields fields;
  private final HistoryMaps maps;
  private final LandmarkMap landmarks;

  /**
   * Builds the history of one world.
   *
   * @param worldSeed the world seed
   * @param config the world engine settings
   */
  public HistoryEngine(long worldSeed, WorldEngineConfig config) {
    this(worldSeed, config, SiteTerrain.ANYWHERE);
  }

  /**
   * Builds the history of one world, with ground the siting can consult.
   *
   * @param worldSeed the world seed
   * @param config the world engine settings
   * @param terrain what the ground is like, so a dam is not sited where there is no water
   */
  public HistoryEngine(long worldSeed, WorldEngineConfig config, SiteTerrain terrain) {
    this.config = config;
    this.fields = new NoiseFields(worldSeed, config.getNoise());
    this.maps = new HistoryMaps(worldSeed, config.getHistory());
    this.landmarks = new LandmarkMap(worldSeed, config.getHistory(), maps, terrain);
  }

  /**
   * Returns the fine-grained fields, which shape texture inside a region rather than the region
   * itself.
   *
   * @return the noise fields
   */
  public NoiseFields fields() {
    return fields;
  }

  /**
   * Returns the world's history layers.
   *
   * @return the maps
   */
  public HistoryMaps maps() {
    return maps;
  }

  /**
   * Returns the world's landmark sites.
   *
   * @return the landmark map
   */
  public LandmarkMap landmarks() {
    return landmarks;
  }

  /**
   * Resolves what happened at a world position.
   *
   * <p>The history layers and the landmarks are read at the exact block. The corruption level is
   * resolved at chunk granularity, as it always has been — that field is what decides how a chunk
   * is treated, and a level that changed mid-chunk would have nothing to apply itself to.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the region profile
   */
  public RegionProfile at(int blockX, int blockZ) {
    return resolve(blockX, blockZ, Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
  }

  /**
   * Resolves what happened in a chunk, sampled at its centre.
   *
   * @param chunkX chunk x coordinate
   * @param chunkZ chunk z coordinate
   * @return the region profile
   */
  public RegionProfile atChunk(int chunkX, int chunkZ) {
    return resolve(chunkX * 16 + 8, chunkZ * 16 + 8, chunkX, chunkZ);
  }

  private RegionProfile resolve(int blockX, int blockZ, int chunkX, int chunkZ) {
    double war = maps.war().at(blockX, blockZ);
    double ashfall = maps.ashfall().at(blockX, blockZ);
    double restoration = maps.restoration().at(blockX, blockZ);

    CorruptionZone zone = CorruptionZone.resolve(
        fields,
        config.getThresholds(),
        config.getLevels(),
        chunkX,
        chunkZ
    );

    // One walk of the nine surrounding cells serves both landmark questions
    List<Landmark> sites = landmarks.near(blockX, blockZ);

    return new RegionProfile(
        zone.level(),
        zone.intensity(),
        war,
        ashfall,
        restoration,
        RegionStory.of(config.getHistory(), war, ashfall, restoration),
        HistoryShaping.shape(zone.profile(), config.getHistory(), war, ashfall, restoration),
        LandmarkMap.coveringIn(sites, blockX, blockZ),
        LandmarkMap.nearestIn(sites, blockX, blockZ)
    );
  }
}
