package com.edysmajler.neweracore.world.infrastructure;

import com.edysmajler.neweracore.world.history.Landmark;

/**
 * One connection between two places, and the line it takes.
 *
 * <p>This is what a later city, factory, or checkpoint generator is meant to ask for. A settlement
 * that knows a highway runs past it can face the road; one that does not can only be dropped on the
 * ground and hope. Everything about a route is derived from its two endpoints, so any part of the
 * world can work out what passes through it without being told.
 */
public final class Route {

  private final RouteType type;
  private final Landmark from;
  private final Landmark to;
  private final RoutePath path;

  /**
   * Creates a route.
   *
   * @param type what kind of connection this is
   * @param from one end
   * @param to the other end
   * @param path the line between them
   */
  public Route(RouteType type, Landmark from, Landmark to, RoutePath path) {
    this.type = type;
    this.from = from;
    this.to = to;
    this.path = path;
  }

  /**
   * Returns what kind of connection this is.
   *
   * @return the type
   */
  public RouteType type() {
    return type;
  }

  /**
   * Returns the place at one end.
   *
   * @return the landmark
   */
  public Landmark from() {
    return from;
  }

  /**
   * Returns the place at the other end.
   *
   * @return the landmark
   */
  public Landmark to() {
    return to;
  }

  /**
   * Returns the line this route follows.
   *
   * @return the path
   */
  public RoutePath path() {
    return path;
  }

  /**
   * Returns the distance from a position to this route.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the distance in blocks
   */
  public double distanceTo(int blockX, int blockZ) {
    return path.distanceTo(blockX, blockZ);
  }

  /**
   * Returns whether a position lies on this route's surface.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the position is within the route's width
   */
  public boolean covers(int blockX, int blockZ) {
    return distanceTo(blockX, blockZ) <= type.halfWidth();
  }

  @Override
  public String toString() {
    return type.label() + " " + from.type() + " → " + to.type();
  }
}
