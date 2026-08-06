package com.edysmajler.neweracore.world.infrastructure;

import com.edysmajler.neweracore.world.history.LandmarkType;

/**
 * The kinds of line drawn between two places.
 *
 * <p>What a route <em>is</em> follows from what it connects, which is the whole reason
 * infrastructure comes before buildings. A military base with a highway running to it explains
 * itself; the same base dropped on empty ground is scenery. Give the world its connections first
 * and everything placed on them afterwards has a reason to be where it is.
 */
public enum RouteType {

  /** The universal connector. Wide, paved, and the thing towns end up strung along. */
  HIGHWAY(7, "highway"),

  /** Freight. Narrower, straighter in feel, and what industry is built against. */
  RAILWAY(5, "railway"),

  /** Not a way through at all: a cleared swath and a line of pylons, going somewhere useful. */
  POWER_LINE(5, "power line");

  private final int width;
  private final String label;

  RouteType(int width, String label) {
    this.width = width;
    this.label = label;
  }

  /**
   * Returns how wide the route is, in blocks.
   *
   * @return the full width
   */
  public int width() {
    return width;
  }

  /**
   * Returns half the width, which is what a distance test wants.
   *
   * @return the half width
   */
  public double halfWidth() {
    return width / 2.0;
  }

  /**
   * Returns a readable name, for debug output.
   *
   * @return the label
   */
  public String label() {
    return label;
  }

  /**
   * Returns whether this route is paved rather than merely cleared.
   *
   * @return true for highways and railways
   */
  public boolean isPaved() {
    return this != POWER_LINE;
  }

  /**
   * Returns the kind of line that would run between two places.
   *
   * <p>Ordered deliberately. Two power places are joined by a line and nothing else, because a
   * power line is not a way to travel. A railway wins over a highway whenever one end wants freight
   * — a works with no rail to it is a works nobody could supply. Everything else is a road, because
   * a road is what connects anything to anything.
   *
   * @param from one end
   * @param to the other end
   * @return the route type
   */
  public static RouteType between(LandmarkType from, LandmarkType to) {
    RouteType wanted = from.connectsBy();
    RouteType other = to.connectsBy();

    if (wanted == other) {
      return wanted;
    }

    if (wanted == RAILWAY || other == RAILWAY) {
      return RAILWAY;
    }

    return HIGHWAY;
  }
}
