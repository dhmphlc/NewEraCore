package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Settings for the world transformation engine.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorldEngineConfig {

  @JsonProperty("enabled")
  private boolean enabled = true;

  @Min(1)
  @Max(64)
  @JsonProperty("scan-depth")
  private int scanDepth = 24;

  @Valid
  @NotNull
  @JsonProperty("noise")
  private NoiseConfig noise = new NoiseConfig();

  @Valid
  @NotNull
  @JsonProperty("thresholds")
  private ThresholdConfig thresholds = new ThresholdConfig();

  @Valid
  @NotNull
  @JsonProperty("levels")
  private LevelsConfig levels = new LevelsConfig();

  @Valid
  @NotNull
  @JsonProperty("history")
  private HistoryConfig history = new HistoryConfig();

  @Valid
  @NotNull
  @JsonProperty("infrastructure")
  private InfrastructureConfig infrastructure = new InfrastructureConfig();

  @Valid
  @NotNull
  @JsonProperty("huge-craters")
  private HugeCraterConfig hugeCraters = new HugeCraterConfig();

  @Valid
  @NotNull
  @JsonProperty("ores")
  private OreConfig ores = new OreConfig();

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Returns how many blocks below each column's surface the engine inspects.
   *
   * @return the scan depth in blocks
   */
  public int getScanDepth() {
    return scanDepth;
  }

  public NoiseConfig getNoise() {
    return noise;
  }

  public ThresholdConfig getThresholds() {
    return thresholds;
  }

  public LevelsConfig getLevels() {
    return levels;
  }

  /**
   * Returns the simulated history: the largest-scale layers, from which every region's character is
   * derived.
   *
   * @return the history config
   */
  public HistoryConfig getHistory() {
    return history;
  }

  /**
   * Returns what was built between the landmarks: roads, rail, and power lines.
   *
   * @return the infrastructure config
   */
  public InfrastructureConfig getInfrastructure() {
    return infrastructure;
  }

  public HugeCraterConfig getHugeCraters() {
    return hugeCraters;
  }

  public OreConfig getOres() {
    return ores;
  }
}
