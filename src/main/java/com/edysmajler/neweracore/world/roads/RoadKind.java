package com.edysmajler.neweracore.world.roads;

/**
 * The two classes of road, and how each one is drawn.
 *
 * <p>Highways are wide, straight-ish, and carry a dashed centre line; local roads are narrow,
 * wander more, and connect towns. The numbers live on the enum so the network geometry and the
 * paving pass can never disagree about what a road of a given class looks like.
 */
public enum RoadKind {

  /** Wide long-distance road on its own sparse grid, with a median line and utility poles. */
  HIGHWAY(3.4, 0.035),

  /** Narrow road connecting neighbouring towns. */
  LOCAL(1.9, 0.06);

  private final double halfWidth;
  private final double bendShare;

  RoadKind(double halfWidth, double bendShare) {
    this.halfWidth = halfWidth;
    this.bendShare = bendShare;
  }

  /**
   * Returns how far paving reaches from the centreline, before edge raggedness.
   *
   * @return the half width in blocks
   */
  public double halfWidth() {
    return halfWidth;
  }

  /**
   * Returns how far the road bends off the straight line between its nodes, as a share of its
   * length.
   *
   * <p>Highways bend less: engineering ran them straight, and a motorway doing S-curves through
   * flat country reads as a river of asphalt.
   *
   * @return the bend amplitude share
   */
  public double bendShare() {
    return bendShare;
  }
}
