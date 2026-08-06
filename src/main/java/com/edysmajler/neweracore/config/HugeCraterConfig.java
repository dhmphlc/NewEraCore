package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Rare impacts big enough to span many chunks.
 *
 * <p>Sites are laid out on a coarse grid rather than rolled per chunk, because a crater wider than
 * a
 * chunk has to be agreed on by every chunk it touches. Spacing is the distance between candidate
 * sites, so a larger spacing means fewer, further apart.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HugeCraterConfig {

  @Min(128)
  @Max(8192)
  @JsonProperty("spacing")
  private int spacing = 768;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("chance")
  private double chance = 0.4;

  @Min(10)
  @Max(48)
  @JsonProperty("radius-min")
  private int radiusMin = 18;

  @Min(10)
  @Max(64)
  @JsonProperty("radius-max")
  private int radiusMax = 36;

  @DecimalMin("0.1")
  @DecimalMax("1.5")
  @JsonProperty("depth-factor")
  private double depthFactor = 0.5;

  /**
   * Returns the distance in blocks between candidate sites.
   *
   * @return the spacing
   */
  public int getSpacing() {
    return spacing;
  }

  /**
   * Returns the chance a candidate site actually holds a crater.
   *
   * @return the chance
   */
  public double getChance() {
    return chance;
  }

  public int getRadiusMin() {
    return radiusMin;
  }

  public int getRadiusMax() {
    return radiusMax;
  }

  /**
   * Returns how deep a crater is relative to its radius.
   *
   * @return the depth factor
   */
  public double getDepthFactor() {
    return depthFactor;
  }
}
