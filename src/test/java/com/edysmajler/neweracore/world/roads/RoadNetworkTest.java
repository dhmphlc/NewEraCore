package com.edysmajler.neweracore.world.roads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.RoadsConfig;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoadNetworkTest {

  private static final long SEED = 987654321L;

  private final RoadsConfig config = new RoadsConfig();

  private RoadPlan plan(int chunkX, int chunkZ) {
    return RoadNetwork.near(config, LandLookup.EVERYWHERE, SEED, chunkX, chunkZ);
  }

  /**
   * Collects a de-duplicated sample of segments by querying chunks across several grid cells.
   */
  private Map<Long, RoadSegment> sampleSegments() {
    Map<Long, RoadSegment> segments = new HashMap<>();

    for (int x = 0; x <= 256; x += 32) {
      for (int z = 0; z <= 256; z += 32) {
        for (RoadSegment segment : plan(x, z).segments()) {
          segments.putIfAbsent(segment.seed(), segment);
        }
      }
    }

    return segments;
  }

  @Test
  void networkExistsAtDefaults() {
    // The defaults must actually produce a network: every other assertion here is vacuous on an
    // empty world, and an empty world is itself the bug
    assertFalse(sampleSegments().isEmpty());
  }

  @Test
  void sameSeedResolvesTheSameNetwork() {
    RoadPlan first = plan(40, 40);
    RoadPlan second = plan(40, 40);

    assertEquals(
        first.segments().stream().map(RoadSegment::seed).toList(),
        second.segments().stream().map(RoadSegment::seed).toList());
    assertEquals(first.towns(), second.towns());
    assertEquals(first.cars(), second.cars());
  }

  /**
   * Returns the plans of the chunks lying along the sampled roads — where the cars actually are.
   */
  private Map<Long, RoadPlan> plansAlongRoads() {
    Map<Long, RoadPlan> plans = new HashMap<>();

    for (RoadSegment segment : sampleSegments().values()) {
      for (double arc = 0.0; arc <= segment.length(); arc += 16.0) {
        double[] point = segment.pointAt(arc);
        int chunkX = Math.floorDiv((int) Math.round(point[0]), 16);
        int chunkZ = Math.floorDiv((int) Math.round(point[1]), 16);

        plans.computeIfAbsent((((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL),
            unused -> plan(chunkX, chunkZ));
      }
      if (plans.size() > 400) {
        break;
      }
    }

    return plans;
  }

  @Test
  void everyChunkTheRoadCrossesSeesIt() {
    // The window invariant, the same one StructureSitesTest guards: a chunk that misses a segment
    // running through it paves nothing, and the road simply stops at the chunk border. Walk each
    // sampled centreline and demand the chunk under every step knows the segment.
    List<RoadSegment> sample = new ArrayList<>(sampleSegments().values());
    assertTrue(sample.size() >= 3, "too few segments to make the walk meaningful");

    for (RoadSegment segment : sample.subList(0, Math.min(6, sample.size()))) {
      for (double arc = 0.0; arc <= segment.length(); arc += 16.0) {
        double[] point = segment.pointAt(arc);
        int chunkX = Math.floorDiv((int) Math.round(point[0]), 16);
        int chunkZ = Math.floorDiv((int) Math.round(point[1]), 16);

        boolean seen = plan(chunkX, chunkZ).segments().stream()
            .anyMatch(candidate -> candidate.seed() == segment.seed());

        assertTrue(seen, "chunk " + chunkX + ", " + chunkZ + " misses a road crossing it at arc "
            + (int) arc + "/" + (int) segment.length());
      }
    }
  }

  @Test
  void everyChunkTheCarTouchesSeesIt() {
    Set<CarSite> cars = new HashSet<>();
    for (RoadPlan plan : plansAlongRoads().values()) {
      cars.addAll(plan.cars());
    }
    assertFalse(cars.isEmpty(), "no cars found on the sampled network");

    for (CarSite car : cars) {
      int minChunkX = (car.x() - CarSite.RADIUS) >> 4;
      int maxChunkX = (car.x() + CarSite.RADIUS) >> 4;
      int minChunkZ = (car.z() - CarSite.RADIUS) >> 4;
      int maxChunkZ = (car.z() + CarSite.RADIUS) >> 4;

      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
          if (!car.touchesChunk(chunkX, chunkZ)) {
            continue;
          }
          assertTrue(plan(chunkX, chunkZ).cars().contains(car),
              "chunk " + chunkX + ", " + chunkZ + " misses a car whose footprint touches it — "
                  + "that chunk may be the last to generate, and then the car never places");
        }
      }
    }
  }

  @Test
  void carsSitOnThePaving() {
    // A car is sampled off its road's centreline but must stay on the asphalt: a wreck in a field
    // twenty blocks from the road reads as a second, unexplained event
    for (RoadPlan plan : plansAlongRoads().values()) {
      for (CarSite car : plan.cars()) {
        double nearest = plan.segments().stream()
            .mapToDouble(segment -> segment.nearest(car.x(), car.z()).distance())
            .min()
            .orElse(Double.MAX_VALUE);

        assertTrue(nearest <= RoadKind.HIGHWAY.halfWidth() + 1.0,
            "car at " + car.x() + ", " + car.z() + " is " + nearest + " blocks off the road");
      }
    }
  }

  @Test
  void nothingExistsWithoutLand() {
    LandLookup allSea = (blockX, blockZ) -> false;

    for (int x = 0; x <= 256; x += 64) {
      for (int z = 0; z <= 256; z += 64) {
        RoadPlan plan = RoadNetwork.near(config, allSea, SEED, x, z);
        assertTrue(plan.segments().isEmpty(), "a road connected two nodes at sea");
        assertTrue(plan.towns().isEmpty(), "a town stood at sea");
      }
    }
  }

  @Test
  void townsCarryTheirRoadDirections() {
    List<TownSite> towns = RoadNetwork.townsAround(
        config, LandLookup.EVERYWHERE, SEED, 0, 0, 8192);
    assertFalse(towns.isEmpty());

    for (TownSite town : towns) {
      for (TownSite.Heading heading : town.roads()) {
        assertEquals(1.0, Math.hypot(heading.x(), heading.z()), 1e-6,
            "a town's road heading is not a unit vector");
      }
    }
  }

  @Test
  void townsAroundSortsNearestFirst() {
    List<TownSite> towns = RoadNetwork.townsAround(
        config, LandLookup.EVERYWHERE, SEED, 500, 500, 8192);
    assertTrue(towns.size() >= 2, "too few towns to check ordering");

    for (int i = 1; i < towns.size(); i++) {
      assertTrue(towns.get(i - 1).distanceTo(500, 500) <= towns.get(i).distanceTo(500, 500));
    }
  }

  @Test
  void roadsCanBeDisabledWhole() {
    // The paver, the car placer, and the town placer all read one plan, so one switch has to
    // silence all three
    RoadsConfig off = new RoadsConfig() {
      @Override
      public boolean isEnabled() {
        return false;
      }
    };

    assertEquals(RoadPlan.EMPTY, RoadNetwork.near(off, LandLookup.EVERYWHERE, SEED, 40, 40));
  }
}
