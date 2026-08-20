package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

/**
 * Overrides for one structure template, keyed by its id in the structures config.
 *
 * <p>Everything here has a default, so a template with no entry behaves exactly as it did before
 * the config existed: enabled, crash decided by the filename, a standard-sized mess, an equal
 * share of the draw.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateConfig {

  /** Used for every template that has no entry of its own. */
  public static final TemplateConfig DEFAULTS = new TemplateConfig();

  @JsonProperty("enabled")
  private boolean enabled = true;

  @JsonProperty("crash")
  private Boolean crash;

  @DecimalMin("0.0")
  @DecimalMax("3.0")
  @JsonProperty("destruction")
  private double destruction = 1.0;

  @DecimalMin("0.001")
  @DecimalMax("100.0")
  @JsonProperty("weight")
  private double weight = 1.0;

  @Pattern(regexp = "[a-z0-9_.-]+")
  @JsonProperty("loot")
  private String loot;

  /**
   * Returns whether this template takes part in the scatter at all.
   *
   * @return false to keep the file or built-in registered nowhere
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Returns whether this template gets the crash treatment.
   *
   * <p>Unset means the filename decides, so {@code <name>.crash.nbt} keeps working with no config
   * entry at all; set, the config wins over the filename.
   *
   * @param filenameSaysCrash what the filename convention decided
   * @return whether to wrap the template in the crash treatment
   */
  public boolean isCrash(boolean filenameSaysCrash) {
    return crash != null ? crash : filenameSaysCrash;
  }

  /**
   * Returns how much of a mess the landing makes, scaling crater, trench, and debris.
   *
   * <p>0 touches nothing — the model is set in with only its footprint cleared. 1 is the standard
   * crash. 3 is devastation.
   *
   * @return the destruction factor
   */
  public double getDestruction() {
    return destruction;
  }

  /**
   * Returns this template's share of the draw when a site picks what stands on it.
   *
   * @return the relative weight
   */
  public double getWeight() {
    return weight;
  }

  /**
   * Returns the loot table this template stocks its containers from.
   *
   * <p>Unset means the convention decides — the caller passes what that is (crash templates
   * default to {@code military}, plain ones to {@code civilian}) — so a template needs no entry to
   * get themed loot. {@code none} switches stocking off.
   *
   * @param fallback the table the convention picked for this template
   * @return the table id to resolve
   */
  public String lootTable(String fallback) {
    return loot != null ? loot : fallback;
  }
}
