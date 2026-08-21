package com.edysmajler.neweracore.plan;

import java.util.List;

/**
 * A designed world: the area it covers, what stands in it, and what connects.
 *
 * <p>The file the planner writes and the plugin will read. It holds decisions, never results — no
 * heights, no blocks, no generated layouts. That split is what lets the same plan be applied to a
 * fresh world at any time, and what keeps the file small enough to read and edit by hand when
 * something needs fixing without opening the tool.
 *
 * <p>The seed is part of the plan because every coordinate in it was chosen by looking at the
 * terrain that seed produces. A plan applied to a different seed is a plan applied to different
 * ground, and the mismatch will not announce itself.
 *
 * @param seed the world seed the plan was designed against
 * @param originX block x of the planned area's lowest corner
 * @param originZ block z of the planned area's lowest corner
 * @param size width of the planned area in blocks
 * @param locations the places the designer has put down
 * @param roads the connections between them
 */
public record WorldPlan(
    long seed,
    int originX,
    int originZ,
    int size,
    List<PlannedLocation> locations,
    List<PlannedRoad> roads
) {

  /**
   * Copies the lists so a plan is immutable from birth, and tolerates a file that omits them.
   */
  public WorldPlan {
    locations = locations == null ? List.of() : List.copyOf(locations);
    roads = roads == null ? List.of() : List.copyOf(roads);
  }

  /**
   * Returns an empty plan over the area a snapshot covers.
   *
   * @param snapshot the exported terrain the plan will be designed against
   * @return a plan with nothing in it yet
   */
  public static WorldPlan emptyFor(WorldSnapshot snapshot) {
    return new WorldPlan(
        snapshot.seed(),
        snapshot.originX(),
        snapshot.originZ(),
        snapshot.size(),
        List.of(),
        List.of()
    );
  }

  /**
   * Returns whether this plan was designed against the world a snapshot came from.
   *
   * <p>Worth asking loudly rather than quietly: opening a plan over the wrong seed puts every
   * marker on ground that was never looked at, and nothing about the map would look wrong.
   *
   * @param snapshot the snapshot to compare against
   * @return true when the seeds match
   */
  public boolean matches(WorldSnapshot snapshot) {
    return seed == snapshot.seed();
  }
}
