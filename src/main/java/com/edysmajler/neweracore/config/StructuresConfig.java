package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Scattered structures: wrecks and ruins dropped into the world as whole shapes.
 *
 * <p>Sites are laid out on a coarse grid rather than rolled per chunk, for the same reason huge
 * craters are: a structure spans chunk borders, so every chunk it touches has to agree on exactly
 * where it stands, which way it faces, and what it is — without loading each other. Spacing is the
 * distance between candidate sites, so a larger spacing means fewer, further apart.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StructuresConfig {

  @JsonProperty("enabled")
  private boolean enabled = true;

  @Min(128)
  @Max(8192)
  @JsonProperty("spacing")
  private int spacing = 512;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("chance")
  private double chance = 0.8;

  @DecimalMin("0.0")
  @DecimalMax("0.5")
  @JsonProperty("jitter")
  private double jitter = 0.4;

  @Valid
  @NotNull
  @JsonProperty("templates")
  private Map<String, @Valid TemplateConfig> templates = new HashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Returns the distance in blocks between candidate sites.
   *
   * @return the spacing
   */
  public int getSpacing() {
    return spacing;
  }

  /**
   * Returns the chance a candidate site actually holds a structure.
   *
   * <p>Below one so the gaps vary instead of falling on an obvious lattice, and not the share that
   * survives: a site still has to suit the structure drawn for it — most need dry land — so some
   * candidates come to nothing.
   *
   * @return the chance
   */
  public double getChance() {
    return chance;
  }

  /**
   * Returns how far a site wanders inside its grid cell, as a share of the cell.
   *
   * @return the jitter
   */
  public double getJitter() {
    return jitter;
  }

  /**
   * Returns the overrides for one template, or the defaults when it has no entry.
   *
   * <p>Never null, so a caller reads settings without caring whether the server owner wrote any —
   * an absent entry is a template behaving exactly as before the config existed.
   *
   * @param id the structure id, as the registry knows it
   * @return that template's settings
   */
  public TemplateConfig templateFor(String id) {
    return templates.getOrDefault(id, TemplateConfig.DEFAULTS);
  }
}
