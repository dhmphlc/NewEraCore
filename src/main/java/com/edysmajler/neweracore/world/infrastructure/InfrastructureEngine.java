package com.edysmajler.neweracore.world.infrastructure;

import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.history.HistoryEngine;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What was built between the places, and the only way to ask about it.
 *
 * <p>The point of this layer is not the roads. It is that <strong>everything built later can ask
 * where the roads are</strong>. A town that knows a highway runs past can sit along it and face it;
 * a depot can back onto the railway; a checkpoint can be put where a road crosses a river, which is
 * exactly where anyone would have put one. None of that is possible if buildings are placed first
 * and roads are drawn to them afterwards, because roads drawn to arbitrary points look drawn.
 *
 * <p>Built on top of {@link HistoryEngine} rather than beside it, since the landmarks it connects
 * are the history layer's. That makes the dependency one-way and the layering plain: history says
 * what happened and where the places are, infrastructure says how they were joined, and everything
 * after that reads both.
 *
 * <p>Pure geometry over a deterministic graph, so it needs nothing loaded and can answer about
 * ground no player has ever reached. Like the history engine, it holds no cache and no mutable
 * state: a query is a few dozen integer hashes and some arithmetic, and both would be a correctness
 * risk on a server generating chunks from more than one thread.
 */
public final class InfrastructureEngine {

  /** How far out to look when asked for the nearest route, in blocks. */
  private static final double SEARCH_REACH = 1200.0;

  private final RouteNetwork network;

  /**
   * Builds the infrastructure of one world.
   *
   * @param history the world's simulated history, which owns the places being joined
   * @param config the world engine settings
   * @param worldSeed the world seed
   */
  public InfrastructureEngine(HistoryEngine history, WorldEngineConfig config, long worldSeed) {
    this.network = new RouteNetwork(
        history.landmarks(),
        config.getInfrastructure(),
        config.getHistory().getLandmarks().getSpacing(),
        worldSeed
    );
  }

  /**
   * Returns every route passing within reach of a position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @param reach how far to look, in blocks
   * @return the routes
   */
  public List<Route> routesNear(int blockX, int blockZ, double reach) {
    return network.near(blockX, blockZ, reach);
  }

  /**
   * Returns the nearest route to a position.
   *
   * <p>What a settlement generator wants: something to face, or to follow.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the nearest route, empty only where nothing was ever connected nearby
   */
  public Optional<Route> nearestRoute(int blockX, int blockZ) {
    return routesNear(blockX, blockZ, SEARCH_REACH).stream()
        .min(Comparator.comparingDouble(route -> route.distanceTo(blockX, blockZ)));
  }

  /**
   * Returns the distance from a position to the nearest route.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the distance in blocks, or {@link Double#MAX_VALUE} when there is nothing near
   */
  public double distanceToRoute(int blockX, int blockZ) {
    return nearestRoute(blockX, blockZ)
        .map(route -> route.distanceTo(blockX, blockZ))
        .orElse(Double.MAX_VALUE);
  }

  /**
   * Returns the route whose surface covers a position, if any.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the route being stood on
   */
  public Optional<Route> routeAt(int blockX, int blockZ) {
    return routesNear(blockX, blockZ, 32.0).stream()
        .filter(route -> route.covers(blockX, blockZ))
        .findFirst();
  }

  /**
   * Returns the network rule itself, for tests and for systems that need the graph rather than the
   * geometry.
   *
   * @return the network
   */
  public RouteNetwork network() {
    return network;
  }
}
