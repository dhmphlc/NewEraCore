package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Ashfall rules for each of the three corruption levels.
 *
 * <p>Note what the levels no longer do: none of them leaves the ground vanilla. Ash falls on the
 * whole
 * world, and the level decides how deep it lies, how much forest survived under it, and whether the
 * land was struck as well as buried. Half a world of untouched grass beside patches of swapped
 * blocks
 * was what made earlier versions look edited rather than ruined.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LevelsConfig {

  @Valid
  @NotNull
  @JsonProperty("recovered")
  private LevelConfig recovered = new LevelConfig()
      .withAsh(0.55, 0.12, 0.15, 4)
      .withTrees(0.35, 0.25, 0.1)
      .withGround(0.3, 0.25)
      .withCraters(0.92, 0.15, 0.0);

  @Valid
  @NotNull
  @JsonProperty("scarred")
  private LevelConfig scarred = new LevelConfig()
      .withAsh(0.85, 0.35, 0.45, 3)
      .withTrees(0.05, 0.55, 0.3)
      .withGround(0.4, 0.6)
      .withCraters(0.68, 1.0, 0.12);

  @Valid
  @NotNull
  @JsonProperty("devastated")
  private LevelConfig devastated = new LevelConfig()
      .withAsh(1.0, 0.7, 0.8, 2)
      .withTrees(0.0, 0.8, 0.5)
      .withGround(0.45, 0.95)
      .withCraters(0.4, 2.0, 0.3);

  public LevelConfig getRecovered() {
    return recovered;
  }

  public LevelConfig getScarred() {
    return scarred;
  }

  public LevelConfig getDevastated() {
    return devastated;
  }
}
