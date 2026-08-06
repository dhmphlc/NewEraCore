package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Where the rare landmark sites sit.
 *
 * <p>Sites live on a coarse grid in world coordinates and are derived by hashing the cell, the same
 * way huge craters are, because a landmark is a fact about the world rather than about a chunk:
 * many chunks have to agree on exactly where it is without loading each other.
 *
 * <p>The spacing bounds are validated rather than merely documented. A landmark you meet every few
 * hundred blocks is scenery; one you meet every few thousand is a destination, and the difference
 * is the whole point of having them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LandmarkConfig {

  @Min(1000)
  @Max(3000)
  @JsonProperty("spacing")
  private int spacing = 1500;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("chance")
  private double chance = 0.85;

  @DecimalMin("0.0")
  @DecimalMax("0.9")
  @JsonProperty("jitter")
  private double jitter = 0.4;

  /**
   * Returns the distance in blocks between grid cells, one candidate site per cell.
   *
   * @return the spacing
   */
  public int getSpacing() {
    return spacing;
  }

  /**
   * Returns the chance a candidate cell actually holds a landmark.
   *
   * <p>Below one, so the gaps between landmarks vary instead of falling on an obvious lattice.
   *
   * @return the chance
   */
  public double getChance() {
    return chance;
  }

  /**
   * Returns how far a site may wander inside its cell, as a share of the cell width.
   *
   * <p>Kept well below one so two sites either side of a cell border cannot end up neighbours: with
   * the defaults, consecutive landmarks land 900 to 2100 blocks apart, and a skipped cell stretches
   * that towards 3000.
   *
   * @return the jitter share
   */
  public double getJitter() {
    return jitter;
  }
}
