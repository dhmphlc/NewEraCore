package com.edysmajler.neweracore.world.roads;

import java.util.List;

/**
 * Everything the road network puts near one chunk, resolved once by the engine.
 *
 * <p>Bundled so the paver, the car placer, and the town placer all read the same resolution — no
 * pass queries the network for itself, for the same reason no pass samples the corruption field
 * for itself: two systems resolving the same seed math independently is one refactor away from
 * disagreeing in the same valley.
 *
 * @param segments the road segments whose paving could reach this chunk
 * @param towns the towns whose footprint touches this chunk
 * @param cars the cars whose footprint touches this chunk
 */
public record RoadPlan(
    List<RoadSegment> segments,
    List<TownSite> towns,
    List<CarSite> cars
) {

  /** The empty plan, for worlds with roads disabled. */
  public static final RoadPlan EMPTY = new RoadPlan(List.of(), List.of(), List.of());

  /**
   * Copies the lists so a plan is immutable from birth.
   */
  public RoadPlan {
    segments = List.copyOf(segments);
    towns = List.copyOf(towns);
    cars = List.copyOf(cars);
  }
}
