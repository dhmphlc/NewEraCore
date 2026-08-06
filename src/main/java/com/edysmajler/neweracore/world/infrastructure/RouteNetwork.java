package com.edysmajler.neweracore.world.infrastructure;

import com.edysmajler.neweracore.config.InfrastructureConfig;
import com.edysmajler.neweracore.world.history.Landmark;
import com.edysmajler.neweracore.world.history.LandmarkMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Which places are joined to which, and therefore where every route runs.
 *
 * <p>The graph is a <strong>Gabriel graph</strong>: two places are joined when no third place lies
 * inside the circle that has the pair as its diameter. It is worth the name because of what it
 * gives for free. It never crosses itself, it always stays connected — it contains the minimum
 * spanning tree — and it produces the branching, roughly-triangular network that real roads between
 * real towns settle into. Joining every pair within range would give a cobweb; joining only nearest
 * neighbours would give disconnected islands.
 *
 * <p><strong>The determinism trap, and how it is avoided.</strong> Every chunk works this out for
 * itself, and they must all reach the same answer or a road will change its mind at a chunk border.
 * The obvious implementation tests a pair against "the places I can see", which is different for
 * every observer — a chunk to the west sees a blocking landmark that a chunk to the east does not,
 * and the road exists on one side of the border and not the other. So the test never looks at what
 * the observer can see. For any pair, the deciding set is the places around <em>the pair's own
 * midpoint</em>, which every observer computes identically because it depends only on the two
 * endpoints. That single choice is what makes a world-scale network safe to draw sixteen blocks at
 * a time.
 */
public final class RouteNetwork {

  private final LandmarkMap landmarks;
  private final InfrastructureConfig config;
  private final int cellSize;
  private final int cellReach;
  private final long worldSeed;

  /**
   * Builds the network reader for one world.
   *
   * @param landmarks the world's landmark sites
   * @param config the infrastructure settings
   * @param cellSize the landmark grid spacing, in blocks
   * @param worldSeed the world seed
   */
  public RouteNetwork(
      LandmarkMap landmarks,
      InfrastructureConfig config,
      int cellSize,
      long worldSeed
  ) {
    this.landmarks = landmarks;
    this.config = config;
    this.cellSize = cellSize;
    this.worldSeed = worldSeed;

    // Derived, not guessed. A route reaching this position can have its far end a full route length
    // away, and a window too small to contain that end would simply not find the route — the road
    // would vanish for exactly the chunks near one of its ends, leaving a gap in a road that every
    // other chunk agrees exists. Widening the window costs candidate pairs, which are cheap to
    // reject; narrowing it costs correctness, which is not recoverable.
    this.cellReach = (int) Math.floor(config.getMaxRouteLength() / (double) cellSize) + 1;
  }

  /**
   * Returns every route passing within reach of a position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @param reach how far from the position to look, in blocks
   * @return the routes, in a stable order
   */
  public List<Route> near(int blockX, int blockZ, double reach) {
    List<Landmark> sites = sitesAround(blockX, blockZ);
    List<Route> routes = new ArrayList<>();

    for (int i = 0; i < sites.size(); i++) {
      for (int j = i + 1; j < sites.size(); j++) {
        Landmark from = sites.get(i);
        Landmark to = sites.get(j);

        // Cheapest test first, and it matters: a chunk can see two dozen places, so two hundred
        // pairs, and building a route's curve to find out it goes nowhere near costs thousands of
        // points each time. Ruling a pair out against the straight line costs a handful of
        // arithmetic, and only what survives is ever built.
        if (!isPlausiblePair(from, to) || !couldPass(from, to, blockX, blockZ, reach)) {
          continue;
        }

        if (!connects(from, to)) {
          continue;
        }

        RoutePath path = pathBetween(from, to);
        if (!path.reaches(blockX, blockZ, reach)) {
          continue;
        }

        routes.add(new Route(RouteType.between(from.type(), to.type()), from, to, path));
      }
    }

    return routes;
  }

  /**
   * Returns whether two places are joined.
   *
   * <p>Public because it is the network's actual rule, and worth being able to test on its own.
   *
   * @param from one end
   * @param to the other end
   * @return true when a route runs between them
   */
  public boolean connects(Landmark from, Landmark to) {
    if (!isPlausiblePair(from, to)) {
      return false;
    }

    double midX = (from.centerX() + to.centerX()) / 2.0;
    double midZ = (from.centerZ() + to.centerZ()) / 2.0;
    double radius = from.distanceTo(to.centerX(), to.centerZ()) / 2.0;

    // Witnesses come from the pair's own midpoint, never from wherever the caller happens to be
    for (Landmark other : sitesAround((int) Math.round(midX), (int) Math.round(midZ))) {
      if (other.equals(from) || other.equals(to)) {
        continue;
      }

      if (other.distanceTo((int) Math.round(midX), (int) Math.round(midZ)) < radius) {
        return false;
      }
    }

    return true;
  }

  /**
   * Returns the line between two places.
   *
   * @param from one end
   * @param to the other end
   * @return the path
   */
  public RoutePath pathBetween(Landmark from, Landmark to) {
    return RoutePath.between(
        from.centerX(), from.centerZ(), to.centerX(), to.centerZ(), worldSeed);
  }

  /**
   * Returns whether a route between two places could come near a position, without building it.
   */
  private static boolean couldPass(
      Landmark from,
      Landmark to,
      int blockX,
      int blockZ,
      double reach
  ) {
    double length = from.distanceTo(to.centerX(), to.centerZ());
    double slack = RoutePath.maxDeviation(length) + reach;

    return RoutePath.distanceToLine(
        blockX, blockZ, from.centerX(), from.centerZ(), to.centerX(), to.centerZ()) <= slack;
  }

  /**
   * Returns whether a pair is close enough to be worth joining at all.
   */
  private boolean isPlausiblePair(Landmark from, Landmark to) {
    return from.distanceTo(to.centerX(), to.centerZ()) <= config.getMaxRouteLength();
  }

  /**
   * Returns every landmark in the cells around a position.
   */
  private List<Landmark> sitesAround(int blockX, int blockZ) {
    int cellX = Math.floorDiv(blockX, cellSize);
    int cellZ = Math.floorDiv(blockZ, cellSize);
    List<Landmark> sites = new ArrayList<>();

    for (int dx = -cellReach; dx <= cellReach; dx++) {
      for (int dz = -cellReach; dz <= cellReach; dz++) {
        landmarks.siteIn(cellX + dx, cellZ + dz).ifPresent(sites::add);
      }
    }

    return sites;
  }
}
