package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The pre-war road network: highways, local roads, the towns they connect, and the cars left on
 * them.
 *
 * <p>Roads are infrastructure from before the event, so unlike everything else the engine builds
 * they run <em>first</em> and the ashfall settles over them — the reserved-column contract in
 * {@code ChunkContext} exists exactly for this. Towns sit on the local-road grid nodes, so a road
 * always leads somewhere and a town always has a road; both are pure functions of the seed, like
 * every other world-scale feature.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoadsConfig {

  @JsonProperty("enabled")
  private boolean enabled = true;

  @Min(256)
  @Max(8192)
  @JsonProperty("town-spacing")
  private int townSpacing = 1024;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("town-chance")
  private double townChance = 0.7;

  @Min(512)
  @Max(16384)
  @JsonProperty("highway-spacing")
  private int highwaySpacing = 3072;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("highway-chance")
  private double highwayChance = 0.85;

  @Min(16)
  @Max(512)
  @JsonProperty("car-spacing")
  private int carSpacing = 56;

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Returns the distance in blocks between candidate town cells.
   *
   * <p>Local roads connect neighbouring towns, so this is also how long a local road runs.
   *
   * @return the spacing
   */
  public int getTownSpacing() {
    return townSpacing;
  }

  /**
   * Returns the chance a candidate cell holds a town.
   *
   * <p>Not the share that appears: a town also needs the generator to put land at its node, so
   * some candidates come to nothing — and their roads with them.
   *
   * @return the chance
   */
  public double getTownChance() {
    return townChance;
  }

  /**
   * Returns the distance in blocks between candidate highway cells.
   *
   * @return the spacing
   */
  public int getHighwaySpacing() {
    return highwaySpacing;
  }

  /**
   * Returns the chance a candidate cell holds a highway node.
   *
   * <p>Kept high by default: a highway that keeps petering out is a country lane with a median.
   *
   * @return the chance
   */
  public double getHighwayChance() {
    return highwayChance;
  }

  /**
   * Returns the average blocks of road per abandoned car.
   *
   * @return the spacing
   */
  public int getCarSpacing() {
    return carSpacing;
  }
}
