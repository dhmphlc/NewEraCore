package com.edysmajler.neweracore.world.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.history.HistoryEngine;
import com.edysmajler.neweracore.world.history.Landmark;
import com.edysmajler.neweracore.world.history.LandmarkType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Asserts the shape of the network, not that the classes wire together.
 *
 * <p>The whole layer stands on one claim — that every chunk works out the same network for itself —
 * and that claim is invisible in the code and catastrophic when wrong: a road would exist on one
 * side of a chunk border and not the other. So it is measured here, from observers deliberately
 * placed far apart.
 */
class RouteNetworkTest {

  private static final long SEED = 20260806L;

  private final WorldEngineConfig config = new WorldEngineConfig();
  private final HistoryEngine history = new HistoryEngine(SEED, config);
  private final InfrastructureEngine engine = new InfrastructureEngine(history, config, SEED);
  private final RouteNetwork network = engine.network();

  @Test
  void bothEndsOfEveryRouteKnowAboutIt() {
    Route route = engine.routesNear(0, 0, 600.0).stream().findFirst().orElseThrow();
    String name = describe(List.of(route)).iterator().next();

    // Standing at either end of a road, ask what runs past. A chunk that cannot see far enough to
    // find the other end never considers the pair at all, draws nothing, and leaves a gap in a road
    // every chunk in the middle agrees exists — which is why the search window is derived from the
    // longest route allowed rather than picked by eye.
    assertTrue(
        describe(engine.routesNear(route.from().centerX(), route.from().centerZ(), 64.0))
            .contains(name),
        "the place at one end does not know the road leaving it"
    );
    assertTrue(
        describe(engine.routesNear(route.to().centerX(), route.to().centerZ(), 64.0))
            .contains(name),
        "the place at the other end does not know the road arriving"
    );
  }

  @Test
  void neighbouringChunksSeeTheSameRoutesThroughTheirBorder() {
    // Two observers either side of a chunk border. Anything one of them has must be known to the
    // other, or a road would exist on one side of the border and stop dead at it.
    Set<String> west = describe(engine.routesNear(-8, 0, 200.0));
    Set<String> east = describe(engine.routesNear(8, 0, 400.0));

    assertFalse(west.isEmpty(), "no routes near the origin to compare");
    assertTrue(east.containsAll(west),
        "the two sides of a chunk border disagree about what runs through it: " + west + " / "
            + east);
  }

  @Test
  void everyPlaceIsReachable() {
    List<Landmark> sites = new ArrayList<>();
    for (int cellX = -3; cellX <= 3; cellX++) {
      for (int cellZ = -3; cellZ <= 3; cellZ++) {
        history.landmarks().siteIn(cellX, cellZ).ifPresent(sites::add);
      }
    }

    int isolated = 0;
    for (Landmark site : sites) {
      boolean joined = sites.stream()
          .anyMatch(other -> !other.equals(site) && network.connects(site, other));

      if (!joined) {
        isolated++;
      }
    }

    // A Gabriel graph contains the minimum spanning tree, so nothing should be stranded. Anything
    // stranded is a landmark no road will ever reach, which is a place no player will ever find.
    assertEquals(0, isolated, isolated + " of " + sites.size() + " places have nothing running to");
  }

  @Test
  void theNetworkIsSparseRatherThanCobwebbed() {
    List<Landmark> sites = new ArrayList<>();
    for (int cellX = -3; cellX <= 3; cellX++) {
      for (int cellZ = -3; cellZ <= 3; cellZ++) {
        history.landmarks().siteIn(cellX, cellZ).ifPresent(sites::add);
      }
    }

    int edges = 0;
    for (int i = 0; i < sites.size(); i++) {
      for (int j = i + 1; j < sites.size(); j++) {
        if (network.connects(sites.get(i), sites.get(j))) {
          edges++;
        }
      }
    }

    double perSite = edges / (double) sites.size();

    // Joining everything to everything would give a cobweb over the map; a Gabriel graph settles at
    // roughly two to three edges per node, which is what a road map looks like.
    assertTrue(perSite > 1.0,
        "only " + perSite + " routes per place: the network is falling apart");
    assertTrue(perSite < 4.0, perSite + " routes per place: that is a cobweb, not a road map");
  }

  @Test
  void whatRunsBetweenTwoPlacesFollowsWhatTheyAre() {
    assertEquals(
        RouteType.RAILWAY,
        RouteType.between(LandmarkType.RESEARCH_FACILITY, LandmarkType.MILITARY_BASE),
        "industry has to be reachable by rail"
    );
    assertEquals(
        RouteType.POWER_LINE,
        RouteType.between(LandmarkType.HYDROELECTRIC_DAM, LandmarkType.RADIO_TOWER)
    );
    assertEquals(
        RouteType.HIGHWAY,
        RouteType.between(LandmarkType.MILITARY_BASE, LandmarkType.HOSPITAL)
    );
    // A power line is not a way to travel: something else has to reach a place you can drive to
    assertEquals(
        RouteType.HIGHWAY,
        RouteType.between(LandmarkType.HYDROELECTRIC_DAM, LandmarkType.AIRPORT)
    );
  }

  @Test
  void routesAreTheSameForTheSameSeed() {
    InfrastructureEngine same = new InfrastructureEngine(
        new HistoryEngine(SEED, config), config, SEED);
    InfrastructureEngine other = new InfrastructureEngine(
        new HistoryEngine(SEED + 1, config), config, SEED + 1);

    assertEquals(describe(engine.routesNear(0, 0, 400.0)),
        describe(same.routesNear(0, 0, 400.0)));
    assertNotEquals(describe(engine.routesNear(0, 0, 400.0)),
        describe(other.routesNear(0, 0, 400.0)));
  }

  private static Set<String> describe(List<Route> routes) {
    Set<String> names = new HashSet<>();

    for (Route route : routes) {
      names.add(route.type() + ":" + route.from().centerX() + "," + route.from().centerZ()
          + "->" + route.to().centerX() + "," + route.to().centerZ());
    }

    return names;
  }
}
