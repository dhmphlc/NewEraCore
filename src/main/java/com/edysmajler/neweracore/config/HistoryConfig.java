package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The simulated history of the world: what happened, how widely, and how hard it still shows.
 *
 * <p>These are the largest scales in the engine by a wide margin. Corruption regions are a few
 * hundred blocks across and decide how deep the ash lies; history layers are a thousand or more and
 * decide <em>what kind of place</em> this was — a battlefield, a fallout plain, a valley the war
 * never reached.
 *
 * <p><strong>The three scales are deliberately unequal and deliberately not multiples of each
 * other.</strong> Equal scales would peak and trough together and the world would divide into a few
 * enormous uniform districts. Unequal ones beat against each other, so the combination changes far
 * more often than any single layer does, and a player walking in one direction keeps crossing into
 * somewhere that reads differently.
 *
 * <p>The influence values are the only knobs that decide how much history bends the corruption
 * numbers. Set all three to zero and the engine behaves exactly as it did before history existed,
 * which makes them the honest off switch.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoryConfig {

  @Min(512)
  @Max(4096)
  @JsonProperty("war-scale")
  private int warScale = 1024;

  @Min(512)
  @Max(4096)
  @JsonProperty("ashfall-scale")
  private int ashfallScale = 768;

  @Min(512)
  @Max(4096)
  @JsonProperty("restoration-scale")
  private int restorationScale = 640;

  @Min(16)
  @Max(256)
  @JsonProperty("restoration-pocket-scale")
  private int restorationPocketScale = 96;

  @DecimalMin("0.5")
  @DecimalMax("1.0")
  @JsonProperty("restoration-pocket-threshold")
  private double restorationPocketThreshold = 0.86;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("restoration-pocket-strength")
  private double restorationPocketStrength = 0.9;

  @Min(1)
  @Max(5)
  @JsonProperty("octaves")
  private int octaves = 3;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("war-high")
  private double warHigh = 0.6;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("ashfall-high")
  private double ashfallHigh = 0.6;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("restoration-high")
  private double restorationHigh = 0.55;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("war-influence")
  private double warInfluence = 0.7;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("ashfall-influence")
  private double ashfallInfluence = 0.6;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("restoration-influence")
  private double restorationInfluence = 0.55;

  @Valid
  @NotNull
  @JsonProperty("landmarks")
  private LandmarkConfig landmarks = new LandmarkConfig();

  /**
   * Returns the width in blocks of one region of the war map.
   *
   * @return the war scale
   */
  public int getWarScale() {
    return warScale;
  }

  /**
   * Returns the width in blocks of one region of the ashfall map.
   *
   * @return the ashfall scale
   */
  public int getAshfallScale() {
    return ashfallScale;
  }

  /**
   * Returns the width in blocks of one region of the restoration map.
   *
   * @return the restoration scale
   */
  public int getRestorationScale() {
    return restorationScale;
  }

  /**
   * Returns the width in blocks of a surviving pocket of green.
   *
   * <p>Small on purpose: this is the layer that puts a living grove inside a burned region, and the
   * contrast only works if the pocket is small enough to see across.
   *
   * @return the pocket scale
   */
  public int getRestorationPocketScale() {
    return restorationPocketScale;
  }

  /**
   * Returns the percentile above which a restoration pocket exists at all.
   *
   * @return the pocket threshold
   */
  public double getRestorationPocketThreshold() {
    return restorationPocketThreshold;
  }

  /**
   * Returns the restoration value the centre of a pocket is lifted to.
   *
   * @return the pocket strength
   */
  public double getRestorationPocketStrength() {
    return restorationPocketStrength;
  }

  /**
   * Returns how many octaves each history map sums.
   *
   * @return the octave count
   */
  public int getOctaves() {
    return octaves;
  }

  /**
   * Returns the war percentile above which a region counts as a war zone.
   *
   * @return the threshold
   */
  public double getWarHigh() {
    return warHigh;
  }

  /**
   * Returns the ashfall percentile above which a region counts as buried.
   *
   * @return the threshold
   */
  public double getAshfallHigh() {
    return ashfallHigh;
  }

  /**
   * Returns the restoration percentile above which nature counts as having held on.
   *
   * @return the threshold
   */
  public double getRestorationHigh() {
    return restorationHigh;
  }

  /**
   * Returns how strongly the war map bends the corruption numbers, 0 for not at all.
   *
   * @return the influence
   */
  public double getWarInfluence() {
    return warInfluence;
  }

  /**
   * Returns how strongly the ashfall map bends the corruption numbers, 0 for not at all.
   *
   * @return the influence
   */
  public double getAshfallInfluence() {
    return ashfallInfluence;
  }

  /**
   * Returns how strongly the restoration map bends the corruption numbers, 0 for not at all.
   *
   * @return the influence
   */
  public double getRestorationInfluence() {
    return restorationInfluence;
  }

  /**
   * Returns the landmark siting rules.
   *
   * @return the landmark config
   */
  public LandmarkConfig getLandmarks() {
    return landmarks;
  }
}
