package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Ashfall rules for one corruption level.
 *
 * <p>The same shape is used for all three levels; only the values differ. Coverage values are
 * shares
 * of area and threshold values are percentiles of a noise field, so raising a coverage or lowering
 * a
 * threshold widens the effect smoothly across whole regions rather than sprinkling more blocks.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LevelConfig {

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("ash-carpet-coverage")
  private double ashCarpetCoverage = 0.9;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("deep-ash-share")
  private double deepAshShare = 0.35;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("drift-chance")
  private double driftChance = 0.3;

  @Min(1)
  @Max(8)
  @JsonProperty("scour-slope")
  private int scourSlope = 3;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("living-grove-threshold")
  private double livingGroveThreshold = 0.12;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("snap-share")
  private double snapShare = 0.6;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("collapse-share")
  private double collapseShare = 0.3;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("dead-bush-chance")
  private double deadBushChance = 0.4;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("water-drying-chance")
  private double waterDryingChance = 0.4;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("impact-zone-threshold")
  private double impactZoneThreshold = 0.62;

  @DecimalMin("0.0")
  @DecimalMax("6.0")
  @JsonProperty("craters-per-zone")
  private double cratersPerZone = 1.0;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @JsonProperty("large-crater-share")
  private double largeCraterShare = 0.12;

  /**
   * Sets the ashfall rules. Used to express per-level defaults.
   *
   * @param carpet share of sheltered ground that takes an ash carpet
   * @param deep share of covered ground reaching the deeper, paler ash
   * @param drift chance a hollow has drifted deep enough to raise the ground
   * @param scour slope at which a face is stripped back to rock
   * @return this config
   */
  LevelConfig withAsh(double carpet, double deep, double drift, int scour) {
    this.ashCarpetCoverage = carpet;
    this.deepAshShare = deep;
    this.driftChance = drift;
    this.scourSlope = scour;
    return this;
  }

  /**
   * Sets the tree rules. Used to express per-level defaults.
   *
   * @param livingGroves blight percentile below which a stand survives
   * @param snapped share of dead trees left as broken snags
   * @param collapsed share of dead trees that come down entirely
   * @return this config
   */
  LevelConfig withTrees(double livingGroves, double snapped, double collapsed) {
    this.livingGroveThreshold = livingGroves;
    this.snapShare = snapped;
    this.collapseShare = collapsed;
    return this;
  }

  /**
   * Sets the ground-clutter and water rules. Used to express per-level defaults.
   *
   * @param deadBush chance a cleared plant leaves a dead bush
   * @param drying chance shallow water has dried out
   * @return this config
   */
  LevelConfig withGround(double deadBush, double drying) {
    this.deadBushChance = deadBush;
    this.waterDryingChance = drying;
    return this;
  }

  /**
   * Sets the crater rules. Used to express per-level defaults.
   *
   * @param zoneThreshold impact percentile above which craters may appear
   * @param perZone expected craters in a chunk inside an impact zone
   * @param largeShare share of those craters that are large
   * @return this config
   */
  LevelConfig withCraters(double zoneThreshold, double perZone, double largeShare) {
    this.impactZoneThreshold = zoneThreshold;
    this.cratersPerZone = perZone;
    this.largeCraterShare = largeShare;
    return this;
  }

  public double getAshCarpetCoverage() {
    return ashCarpetCoverage;
  }

  public double getDeepAshShare() {
    return deepAshShare;
  }

  public double getDriftChance() {
    return driftChance;
  }

  public int getScourSlope() {
    return scourSlope;
  }

  public double getLivingGroveThreshold() {
    return livingGroveThreshold;
  }

  public double getSnapShare() {
    return snapShare;
  }

  public double getCollapseShare() {
    return collapseShare;
  }

  public double getDeadBushChance() {
    return deadBushChance;
  }

  public double getWaterDryingChance() {
    return waterDryingChance;
  }

  public double getImpactZoneThreshold() {
    return impactZoneThreshold;
  }

  public double getCratersPerZone() {
    return cratersPerZone;
  }

  public double getLargeCraterShare() {
    return largeCraterShare;
  }
}
