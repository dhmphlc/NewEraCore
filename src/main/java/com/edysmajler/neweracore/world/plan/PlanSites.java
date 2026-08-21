package com.edysmajler.neweracore.world.plan;

import com.edysmajler.neweracore.plan.PlannedLocation;
import com.edysmajler.neweracore.plan.PlannedRoad;
import com.edysmajler.neweracore.plan.WorldPlan;
import com.edysmajler.neweracore.world.structures.StructureSite;
import com.edysmajler.neweracore.world.towns.TownSite;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a designer's plan into the site records the placement systems already know how to build.
 *
 * <p>Deliberately a translation layer and nothing more. The town and structure placers were
 * written against seeded sites, and they are the tested, shipped code for putting a settlement or
 * a wreck on the ground; a plan-specific builder beside them would be a second implementation of
 * the same job, drifting from the first. So a planned location becomes a {@link TownSite} or a
 * {@link StructureSite} and goes through the same door.
 *
 * <p>Everything here is a pure function of the plan and the world seed, which is what lets a
 * chunk decide its part in a planned placement without loading anything — the same property that
 * makes the seeded grid work.
 */
public final class PlanSites {

  private PlanSites() {}

  /**
   * Returns whether a planned location's footprint reaches into a chunk.
   *
   * <p>Measured to the nearest point of the chunk rather than its centre, for the reason both
   * seeded systems record in their own tests: the chunk that misses a footprint it touches may be
   * the last one to generate, and then nothing is ever built.
   *
   * @param location the planned location
   * @param chunkX chunk x coordinate
   * @param chunkZ chunk z coordinate
   * @return true when the footprint overlaps the chunk
   */
  public static boolean touchesChunk(PlannedLocation location, int chunkX, int chunkZ) {
    int originX = chunkX * 16;
    int originZ = chunkZ * 16;
    double nearestX = Math.clamp(location.blockX(), originX, originX + 15);
    double nearestZ = Math.clamp(location.blockZ(), originZ, originZ + 15);
    return Math.hypot(location.blockX() - nearestX, location.blockZ() - nearestZ)
        <= location.radius();
  }

  /**
   * Returns the planned locations whose footprint reaches into a chunk.
   *
   * @param locations every location in the plan
   * @param chunkX chunk x coordinate
   * @param chunkZ chunk z coordinate
   * @return the locations touching this chunk, usually none
   */
  public static List<PlannedLocation> touching(
      List<PlannedLocation> locations,
      int chunkX,
      int chunkZ
  ) {
    List<PlannedLocation> found = new ArrayList<>();

    for (PlannedLocation location : locations) {
      if (touchesChunk(location, chunkX, chunkZ)) {
        found.add(location);
      }
    }

    return found;
  }

  /**
   * Returns whether a position falls inside a planned location's ground.
   *
   * <p>What the seeded systems are kept out of. A procedural town a few blocks from a designed one
   * is worse than either alone: the designed one stops reading as deliberate, which was the whole
   * reason for placing it by hand.
   *
   * @param locations every location in the plan
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @param clearance how far beyond each radius to keep clear, in blocks
   * @return true when the position belongs to a planned location
   */
  public static boolean isReserved(
      List<PlannedLocation> locations,
      int blockX,
      int blockZ,
      int clearance
  ) {
    for (PlannedLocation location : locations) {
      if (location.distanceTo(blockX, blockZ) <= location.radius() + clearance) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns the seed a planned placement builds from.
   *
   * <p>Mixed from the world seed and the location's id, so the same plan on the same seed always
   * produces the same town — and renaming a location does not reshuffle it, while moving one does
   * not either. The id is the one part of a location a designer is least likely to churn, which is
   * exactly what a stable seed wants to hang off.
   *
   * @param worldSeed the world seed
   * @param location the planned location
   * @return the placement seed
   */
  public static long seedFor(long worldSeed, PlannedLocation location) {
    long hash = worldSeed ^ (location.id().hashCode() * 0x9E3779B97F4A7C15L);
    hash ^= hash >>> 33;
    hash *= 0xFF51AFD7ED558CCDL;
    hash ^= hash >>> 33;
    hash *= 0xC4CEB9FE1A85EC53L;
    hash ^= hash >>> 33;
    return hash;
  }

  /**
   * Returns the street directions a planned settlement's houses line up along.
   *
   * <p>The plan's roads, reused. A seeded town points its streets at its nearest neighbours because
   * that is the best guess available without a designer; here there is a designer, and the roads
   * they drew are a better statement of the same thing. A settlement nobody connected keeps the
   * town placer's own fallback rather than being given invented directions.
   *
   * @param plan the whole plan, for its roads
   * @param location the settlement
   * @return unit vectors towards each connected location, possibly empty
   */
  public static List<TownSite.Heading> streetsFor(WorldPlan plan, PlannedLocation location) {
    List<TownSite.Heading> streets = new ArrayList<>();

    for (PlannedRoad road : plan.roads()) {
      String otherId = otherEnd(road, location.id());
      if (otherId == null) {
        continue;
      }

      for (PlannedLocation other : plan.locations()) {
        if (!other.id().equals(otherId)) {
          continue;
        }

        double dx = other.blockX() - (double) location.blockX();
        double dz = other.blockZ() - (double) location.blockZ();
        double length = Math.hypot(dx, dz);

        if (length > 0.0) {
          streets.add(new TownSite.Heading(dx / length, dz / length));
        }
      }
    }

    return streets;
  }

  /**
   * Returns the town site a planned settlement builds as.
   *
   * @param plan the whole plan, for its roads
   * @param location the settlement
   * @param worldSeed the world seed
   * @return the site, ready for the town placer
   */
  public static TownSite townSiteFor(
      WorldPlan plan,
      PlannedLocation location,
      long worldSeed
  ) {
    return new TownSite(
        location.blockX(),
        location.blockZ(),
        location.radius(),
        seedFor(worldSeed, location),
        streetsFor(plan, location)
    );
  }

  /**
   * Returns the structure site a planned location builds as.
   *
   * <p>The footprint radius comes from the structure rather than from the plan: the definition is
   * the only thing that knows how far its own silhouette reaches, and a designer's radius that
   * understated it would leave a chunk out of the trigger and the wreck unbuilt. The planned radius
   * still governs what ground the location reserves.
   *
   * @param location the planned location
   * @param structureId which structure stands here
   * @param structureRadius the structure's own footprint radius
   * @param worldSeed the world seed
   * @return the site, ready for the structure definition to build
   */
  public static StructureSite structureSiteFor(
      PlannedLocation location,
      String structureId,
      int structureRadius,
      long worldSeed
  ) {
    long seed = seedFor(worldSeed, location);

    return new StructureSite(
        structureId,
        location.blockX(),
        location.blockZ(),
        (int) Math.floorMod(seed >> 16, 4L),
        structureRadius,
        seed
    );
  }

  private static String otherEnd(PlannedRoad road, String id) {
    if (road.fromId().equals(id)) {
      return road.toId();
    }
    if (road.toId().equals(id)) {
      return road.fromId();
    }
    return null;
  }
}
