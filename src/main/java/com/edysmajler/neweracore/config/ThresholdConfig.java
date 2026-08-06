package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Where the corruption level bands begin on the corruption field.
 *
 * <p>The corruption field is calibrated to spread evenly over 0 to 1, so these are literally the
 * shares of the world at each level. With the defaults: 34% recovered, 34% scarred, 32% devastated
 * —
 * enough devastation to define the world's character, with enough intact land left for contrast.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThresholdConfig {

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("scarred-above")
  private double scarredAbove = 0.34;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("devastated-above")
  private double devastatedAbove = 0.68;

  public double getScarredAbove() {
    return scarredAbove;
  }

  public double getDevastatedAbove() {
    return devastatedAbove;
  }
}
