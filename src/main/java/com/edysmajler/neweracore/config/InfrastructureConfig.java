package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * What was built between the landmarks, before anything was built on top of it.
 *
 * <p>Infrastructure comes first for a reason that has nothing to do with rendering order. Towns do
 * not appear at random coordinates; they grow where two roads meet, and industry sits where the
 * rail reaches. Lay the network down before any building exists and everything placed afterwards
 * has a reason to be where it is — which is the difference between a world that was built and a
 * world that was scattered.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InfrastructureConfig {

  @JsonProperty("enabled")
  private boolean enabled = true;

  @Min(600)
  @Max(6000)
  @JsonProperty("max-route-length")
  private int maxRouteLength = 3200;

  @Min(0)
  @Max(12)
  @JsonProperty("clearance")
  private int clearance = 5;

  @DecimalMin("0.0")
  @DecimalMax("0.9")
  @JsonProperty("decay")
  private double decay = 0.3;

  @Min(8)
  @Max(64)
  @JsonProperty("pylon-spacing")
  private int pylonSpacing = 24;

  @Min(4)
  @Max(24)
  @JsonProperty("pylon-height")
  private int pylonHeight = 9;

  @Min(4)
  @Max(24)
  @JsonProperty("bridge-pillar-spacing")
  private int bridgePillarSpacing = 8;

  @Min(60)
  @Max(600)
  @JsonProperty("runway-length")
  private int runwayLength = 260;

  @Min(6)
  @Max(48)
  @JsonProperty("runway-width")
  private int runwayWidth = 18;

  @Min(0)
  @Max(24)
  @JsonProperty("runway-lift")
  private int runwayLift = 10;

  @Min(0)
  @Max(400)
  @JsonProperty("approach-reach")
  private int approachReach = 130;

  @Min(2)
  @Max(24)
  @JsonProperty("approach-slope")
  private int approachSlope = 7;

  /**
   * Returns whether anything is built between the landmarks at all.
   *
   * @return true when the network is laid down
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Returns how far apart two places may be and still be joined, in blocks.
   *
   * <p>A ceiling on ambition. Without it, a pair with nothing between them for miles would still be
   * joined, and a road would run for kilometres across empty country to reach one radio mast.
   *
   * @return the maximum route length
   */
  public int getMaxRouteLength() {
    return maxRouteLength;
  }

  /**
   * Returns how many blocks of headroom a route keeps clear above its surface.
   *
   * <p>What turns a line on the ground into something you can actually travel: without it the road
   * is laid perfectly and then buried under the canopy and the ashfall that come after it.
   *
   * @return the clearance in blocks
   */
  public int getClearance() {
    return clearance;
  }

  /**
   * Returns the share of a route's surface that has broken up.
   *
   * <p>Nobody has maintained any of this for a very long time. A road in perfect repair reads as
   * more recently abandoned than the world around it.
   *
   * @return the decayed share
   */
  public double getDecay() {
    return decay;
  }

  /**
   * Returns the distance between pylons on a power line, in blocks.
   *
   * @return the spacing
   */
  public int getPylonSpacing() {
    return pylonSpacing;
  }

  /**
   * Returns how tall a pylon stands above the ground, in blocks.
   *
   * @return the height
   */
  public int getPylonHeight() {
    return pylonHeight;
  }

  /**
   * Returns the distance between the pillars holding a bridge up, in blocks.
   *
   * @return the spacing
   */
  public int getBridgePillarSpacing() {
    return bridgePillarSpacing;
  }

  /**
   * Returns how long a runway is, in blocks.
   *
   * <p>Long enough to read as a runway from the air and from the ground, which is the only reason
   * an airport is worth having as a landmark at all.
   *
   * @return the length
   */
  public int getRunwayLength() {
    return runwayLength;
  }

  /**
   * Returns how wide the paved strip of a runway is, in blocks.
   *
   * @return the width
   */
  public int getRunwayWidth() {
    return runwayWidth;
  }

  /**
   * Returns how far past the runway the ground has to stay out of the way, in blocks.
   *
   * <p>Levelling the strip is only half of it. An aircraft has to get down to the strip and back
   * off it again, and it cannot fly through a hill sitting off the end of the runway. Real
   * airfields call this the obstacle limitation surface, and it is the reason an airport is a wide
   * flat place rather than a wide flat line.
   *
   * @return the approach reach
   */
  public int getApproachReach() {
    return approachReach;
  }

  /**
   * Returns how steeply the approach surface may rise, as blocks out per block up.
   *
   * <p>Seven is a shallow climb, which is the point: anything poking through the sloping surface is
   * cut off, so the ground falls away from the runway rather than closing in on it.
   *
   * @return the slope
   */
  public int getApproachSlope() {
    return approachSlope;
  }

  /**
   * Returns how far above sea level a runway platform may sit, in blocks.
   *
   * <p>The platform has to be a height every chunk can work out for itself, and sea level is the
   * one global height a world gives away for free. Rolling a small lift on top of it keeps every
   * airfield in the world from sitting at exactly the same altitude.
   *
   * @return the greatest lift above sea level
   */
  public int getRunwayLift() {
    return runwayLift;
  }
}
