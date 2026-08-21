package com.edysmajler.neweracore.plan;

import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.corruption.CorruptionZone;
import com.edysmajler.neweracore.world.feature.CraterSite;
import com.edysmajler.neweracore.world.feature.CraterSites;
import com.edysmajler.neweracore.world.noise.NoiseFields;
import com.edysmajler.neweracore.world.structures.StructureManager;
import com.edysmajler.neweracore.world.structures.StructureSite;
import com.edysmajler.neweracore.world.structures.StructureSites;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import com.edysmajler.neweracore.world.towns.TownSite;
import com.edysmajler.neweracore.world.towns.TownSites;
import java.util.ArrayList;
import java.util.List;

/**
 * One place to ask what the engine has decided about a position, without loading anything.
 *
 * <p>Everything the engine invents is a pure function of the seed, but that fact was spread across
 * five classes, each with its own parameter list of config sections, fields and lookups. Every
 * caller that wanted an answer — a command, an export, a future planning tool — had to assemble
 * those arguments correctly, and assembling them differently is how two parts of the same plugin
 * come to disagree about where a town is. This is the seam the world planner asked for: the callers
 * name a coordinate, and the wiring lives here once.
 *
 * <p>Deliberately holds no reference to a {@code World}. Everything it answers depends on the seed
 * alone, so it answers about terrain that has never generated — which is the whole point, since a
 * plan is made before the world is built. The one thing it cannot answer is what Mojang's generator
 * puts on the ground: height, biome and water need a real world, so they arrive through
 * {@link LandLookup} and, for a raster, through {@link WorldSnapshot}.
 */
public final class WorldQuery {

  private final WorldEngineConfig config;
  private final NoiseFields fields;
  private final StructureManager structures;
  private final LandLookup land;
  private final long seed;

  /**
   * Creates a query over one world's seed.
   *
   * @param config the world engine settings the world was or will be built with
   * @param fields the world's calibrated noise fields
   * @param structures the registry of scattered structures
   * @param land what the generator puts at a position, land or open water
   * @param seed the world seed
   */
  public WorldQuery(
      WorldEngineConfig config,
      NoiseFields fields,
      StructureManager structures,
      LandLookup land,
      long seed
  ) {
    this.config = config;
    this.fields = fields;
    this.structures = structures;
    this.land = land;
    this.seed = seed;
  }

  /**
   * Returns the seed every answer here derives from.
   *
   * @return the world seed
   */
  public long seed() {
    return seed;
  }

  /**
   * Returns the broad corruption field at a position, as a percentile.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the value between 0 and 1
   */
  public double corruptionAt(int blockX, int blockZ) {
    return fields.corruption().sample(blockX, blockZ);
  }

  /**
   * Returns the impact field at a position, which is where craters cluster.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the value between 0 and 1
   */
  public double impactAt(int blockX, int blockZ) {
    return fields.impact().sample(blockX, blockZ);
  }

  /**
   * Returns the blight field at a position, which groups tree death into whole stands.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the value between 0 and 1
   */
  public double blightAt(int blockX, int blockZ) {
    return fields.blight().sample(blockX, blockZ);
  }

  /**
   * Returns the corruption zone the chunk containing a position belongs to.
   *
   * <p>Resolved per chunk rather than per block because that is how the engine resolves it: the
   * level comes from the field sampled at the chunk centre, and reporting a smoother per-block
   * value would describe a world the engine does not build.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the zone, level and blended profile together
   */
  public CorruptionZone zoneAt(int blockX, int blockZ) {
    return CorruptionZone.resolve(
        fields,
        config.getThresholds(),
        config.getLevels(),
        blockX >> 4,
        blockZ >> 4
    );
  }

  /**
   * Returns whether the generator puts land rather than open water at a position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true on land
   */
  public boolean isLand(int blockX, int blockZ) {
    return land.isLand(blockX, blockZ);
  }

  /**
   * Returns whether the ground at a position is broken enough that nothing wants to build on it.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true on hills, slopes and peaks
   */
  public boolean isRugged(int blockX, int blockZ) {
    return land.isRugged(blockX, blockZ);
  }

  /**
   * Returns every feature the engine will place within a radius of a position, nearest kind first.
   *
   * <p>The three systems are asked through their own {@code around} methods rather than
   * re-implemented, so a site listed here is the site that will be built — including the refusals,
   * since a structure whose ground does not suit it is simply absent from the answer.
   *
   * @param blockX absolute block x to search around
   * @param blockZ absolute block z to search around
   * @param blockRadius how far to look, in blocks
   * @return the sites found
   */
  public List<PlannedSite> sitesWithin(int blockX, int blockZ, int blockRadius) {
    List<PlannedSite> found = new ArrayList<>();

    for (StructureSite site : StructureSites.around(
        config.getStructures(), structures, land, seed, blockX, blockZ, blockRadius)) {
      found.add(new PlannedSite(
          PlannedSite.SiteKind.STRUCTURE,
          site.structureId(),
          site.centerX(),
          site.centerZ(),
          site.radius(),
          site.rotation()
      ));
    }

    for (TownSite town : TownSites.around(
        config.getTowns(), land, seed, blockX, blockZ, blockRadius)) {
      found.add(new PlannedSite(
          PlannedSite.SiteKind.TOWN,
          "town",
          town.centerX(),
          town.centerZ(),
          town.radius(),
          0
      ));
    }

    for (CraterSite crater : CraterSites.around(
        config.getHugeCraters(), fields, config.getThresholds(), land, seed,
        blockX, blockZ, blockRadius)) {
      found.add(new PlannedSite(
          PlannedSite.SiteKind.CRATER,
          "huge_crater",
          crater.centerX(),
          crater.centerZ(),
          crater.radius(),
          0
      ));
    }

    return found;
  }
}
