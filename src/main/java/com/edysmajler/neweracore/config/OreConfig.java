package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * How much ore an impact leaves exposed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OreConfig {

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("small-crater-chance")
  private double smallCraterChance = 0.07;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("huge-crater-chance")
  private double hugeCraterChance = 0.18;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("iron-share")
  private double ironShare = 0.2;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("precious-share")
  private double preciousShare = 0.12;

  /**
   * Returns the chance a debris block in a small or medium crater is ore instead.
   *
   * @return the chance
   */
  public double getSmallCraterChance() {
    return smallCraterChance;
  }

  /**
   * Returns the chance a debris block in a huge crater is ore instead.
   *
   * @return the chance
   */
  public double getHugeCraterChance() {
    return hugeCraterChance;
  }

  /**
   * Returns the share of exposed ore that is iron rather than coal or copper.
   *
   * @return the share
   */
  public double getIronShare() {
    return ironShare;
  }

  /**
   * Returns the share of ore in a huge crater that is something better than iron.
   *
   * @return the share
   */
  public double getPreciousShare() {
    return preciousShare;
  }
}
