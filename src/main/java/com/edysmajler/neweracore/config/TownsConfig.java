package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Ruined towns: clusters of collapsed houses scattered on a seeded grid.
 *
 * <p>Towns sit on a coarse hashed grid like structure sites, so every chunk a town touches agrees
 * where it stands without loading anything, and {@code /nec locate town} works over terrain that
 * has never generated.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TownsConfig {

  @JsonProperty("enabled")
  private boolean enabled = true;

  @Min(256)
  @Max(8192)
  @JsonProperty("spacing")
  private int spacing = 1024;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("chance")
  private double chance = 0.7;

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Returns the distance in blocks between candidate town cells.
   *
   * @return the spacing
   */
  public int getSpacing() {
    return spacing;
  }

  /**
   * Returns the chance a candidate cell holds a town.
   *
   * <p>Not the share that appears: a town also needs the generator to put land at its node, so
   * coastal candidates come to nothing.
   *
   * @return the chance
   */
  public double getChance() {
    return chance;
  }
}
