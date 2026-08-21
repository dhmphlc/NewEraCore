package com.edysmajler.neweracore.world.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.plan.LocationType;
import com.edysmajler.neweracore.plan.PlannedLocation;
import com.edysmajler.neweracore.plan.PlannedRoad;
import com.edysmajler.neweracore.plan.WorldPlan;
import com.edysmajler.neweracore.world.structures.StructureSite;
import com.edysmajler.neweracore.world.towns.TownSite;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanSitesTest {

  private static final long SEED = 7153137573198281821L;

  private static PlannedLocation town(String id, int x, int z, int radius) {
    return new PlannedLocation(id, LocationType.TOWN, id, x, z, radius, "");
  }

  @Test
  @DisplayName("every chunk a footprint touches sees the location")
  void everyFootprintChunkSeesIt() {
    // The invariant both seeded systems guard in their own tests, and for the same reason: the
    // chunk that misses a footprint it touches may be the last to generate, and then nothing is
    // ever built there.
    PlannedLocation location = town("haven", 100, 100, 48);
    int found = 0;

    for (int chunkX = -2; chunkX <= 12; chunkX++) {
      for (int chunkZ = -2; chunkZ <= 12; chunkZ++) {
        if (!PlanSites.touchesChunk(location, chunkX, chunkZ)) {
          continue;
        }

        found++;
        // Every chunk that claims the footprint must genuinely overlap it
        double nearestX = Math.clamp(100, chunkX * 16, chunkX * 16 + 15);
        double nearestZ = Math.clamp(100, chunkZ * 16, chunkZ * 16 + 15);
        assertTrue(Math.hypot(100 - nearestX, 100 - nearestZ) <= 48,
            "chunk " + chunkX + ", " + chunkZ + " claimed a footprint it does not touch");
      }
    }

    // A 48-block radius spans seven chunks across at worst, so a plausible count is well above one
    assertTrue(found >= 36, "expected the footprint to span many chunks, saw " + found);
  }

  @Test
  @DisplayName("a chunk holding the centre always sees it, however small the radius")
  void centreChunkAlwaysSeesIt() {
    assertTrue(PlanSites.touchesChunk(town("dot", 8, 8, 1), 0, 0));
    assertFalse(PlanSites.touchesChunk(town("dot", 8, 8, 1), 5, 5));
  }

  @Test
  @DisplayName("only the locations touching a chunk are returned")
  void touchingFilters() {
    List<PlannedLocation> locations = List.of(
        town("near", 8, 8, 32),
        town("far", 2000, 2000, 32)
    );

    assertEquals(List.of(locations.get(0)), PlanSites.touching(locations, 0, 0));
    assertTrue(PlanSites.touching(locations, 40, 40).isEmpty());
  }

  @Test
  @DisplayName("planned ground reaches a clearance beyond the radius")
  void reservedGroundIncludesClearance() {
    List<PlannedLocation> locations = List.of(town("haven", 0, 0, 100));

    assertTrue(PlanSites.isReserved(locations, 90, 0, 32));
    assertTrue(PlanSites.isReserved(locations, 130, 0, 32));
    assertFalse(PlanSites.isReserved(locations, 133, 0, 32));
    // Without clearance the edge is the radius itself
    assertFalse(PlanSites.isReserved(locations, 105, 0, 0));
  }

  @Test
  @DisplayName("a placement seed follows the id, not the name or the position")
  void seedFollowsTheId() {
    PlannedLocation original = town("haven", 100, 100, 48);
    PlannedLocation renamed =
        new PlannedLocation("haven", LocationType.CITY, "Newhaven", 900, -40, 300, "moved");

    // Renaming or moving a location must not reshuffle its layout: the id is the part a designer
    // is least likely to churn, so it is what the seed hangs off
    assertEquals(PlanSites.seedFor(SEED, original), PlanSites.seedFor(SEED, renamed));
    assertNotEquals(PlanSites.seedFor(SEED, original),
        PlanSites.seedFor(SEED, town("other", 100, 100, 48)));
    assertNotEquals(PlanSites.seedFor(SEED, original), PlanSites.seedFor(SEED + 1, original));
  }

  @Test
  @DisplayName("the plan's roads become the town's street headings")
  void roadsBecomeStreets() {
    PlannedLocation haven = town("haven", 0, 0, 48);
    PlannedLocation east = town("east", 1000, 0, 48);
    PlannedLocation south = town("south", 0, 500, 48);
    WorldPlan plan = new WorldPlan(SEED, -2000, -2000, 4000,
        List.of(haven, east, south),
        List.of(new PlannedRoad("haven", "east"), new PlannedRoad("south", "haven")));

    List<TownSite.Heading> streets = PlanSites.streetsFor(plan, haven);

    assertEquals(2, streets.size());
    // Unit vectors, pointing at each connected neighbour, whichever end of the road it was
    assertEquals(1.0, streets.get(0).x(), 1e-9);
    assertEquals(0.0, streets.get(0).z(), 1e-9);
    assertEquals(0.0, streets.get(1).x(), 1e-9);
    assertEquals(1.0, streets.get(1).z(), 1e-9);
  }

  @Test
  @DisplayName("a settlement nobody connected gets no invented streets")
  void unconnectedTownHasNoStreets() {
    PlannedLocation lonely = town("lonely", 0, 0, 48);
    WorldPlan plan = new WorldPlan(SEED, 0, 0, 512, List.of(lonely), List.of());

    // Empty, so the town placer's own compass fallback applies rather than a made-up direction
    assertTrue(PlanSites.streetsFor(plan, lonely).isEmpty());
  }

  @Test
  @DisplayName("a planned town becomes a town site at the planned position and radius")
  void townSiteCarriesThePlan() {
    PlannedLocation haven = town("haven", 482, -317, 180);
    WorldPlan plan = new WorldPlan(SEED, -2000, -2000, 4000, List.of(haven), List.of());

    TownSite site = PlanSites.townSiteFor(plan, haven, SEED);

    assertEquals(482, site.centerX());
    assertEquals(-317, site.centerZ());
    assertEquals(180, site.radius());
    assertEquals(PlanSites.seedFor(SEED, haven), site.seed());
  }

  @Test
  @DisplayName("a planned structure takes its footprint from the structure, not the designer")
  void structureSiteUsesTheStructureRadius() {
    // Only the definition knows how far its silhouette reaches; a designer's smaller radius would
    // leave a footprint chunk out of the trigger and the wreck unbuilt
    PlannedLocation crash =
        new PlannedLocation("crash_1", LocationType.CRASH_SITE, "Crash", 40, 80, 10, "");

    StructureSite site = PlanSites.structureSiteFor(crash, "fighter_jet", 96, SEED);

    assertEquals("fighter_jet", site.structureId());
    assertEquals(96, site.radius());
    assertEquals(40, site.centerX());
    assertEquals(80, site.centerZ());
    assertTrue(site.rotation() >= 0 && site.rotation() <= 3, "rotation " + site.rotation());
  }

  @Test
  @DisplayName("a planned structure's rotation is stable across placements")
  void rotationIsDeterministic() {
    PlannedLocation crash =
        new PlannedLocation("crash_1", LocationType.CRASH_SITE, "Crash", 40, 80, 10, "");

    assertEquals(
        PlanSites.structureSiteFor(crash, "fighter_jet", 96, SEED).rotation(),
        PlanSites.structureSiteFor(crash, "fighter_jet", 96, SEED).rotation()
    );
  }
}
