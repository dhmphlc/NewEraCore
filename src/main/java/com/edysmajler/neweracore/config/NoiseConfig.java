package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Scales of the noise fields that drive every transformation.
 *
 * <p>Each scale is the approximate width in blocks of one feature of that field. Larger corruption
 * scales mean broader devastated and recovered regions; larger patch scales mean fewer, bigger dead
 * areas rather than many small ones.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NoiseConfig {

  @Min(32)
  @Max(4096)
  @JsonProperty("corruption-scale")
  private int corruptionScale = 384;

  @Min(4)
  @Max(256)
  @JsonProperty("patch-scale")
  private int patchScale = 36;

  @Min(4)
  @Max(512)
  @JsonProperty("blight-scale")
  private int blightScale = 56;

  @Min(8)
  @Max(1024)
  @JsonProperty("impact-scale")
  private int impactScale = 128;

  @Min(2)
  @Max(64)
  @JsonProperty("detail-scale")
  private int detailScale = 20;

  @Min(1)
  @Max(5)
  @JsonProperty("octaves")
  private int octaves = 3;

  public int getCorruptionScale() {
    return corruptionScale;
  }

  public int getPatchScale() {
    return patchScale;
  }

  public int getBlightScale() {
    return blightScale;
  }

  public int getImpactScale() {
    return impactScale;
  }

  public int getDetailScale() {
    return detailScale;
  }

  public int getOctaves() {
    return octaves;
  }
}
