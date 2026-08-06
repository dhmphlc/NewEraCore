package com.edysmajler.neweracore.world.noise;

import com.edysmajler.neweracore.config.NoiseConfig;

/**
 * The set of independent noise fields the engine reads.
 *
 * <p>Built once per world and reused for every chunk: constructing a field builds permutation
 * tables, which is wasteful per chunk and pointless since the fields only depend on the world seed.
 *
 * <p>Every transformation decision comes from one of these fields rather than from a dice roll,
 * which is what makes damage appear in coherent regions and patches instead of block by block.
 */
public final class NoiseFields {

  private static final long CORRUPTION_SALT = 0x1L;
  private static final long PATCH_SALT = 0x2L;
  private static final long BLIGHT_SALT = 0x3L;
  private static final long IMPACT_SALT = 0x4L;
  private static final long DETAIL_SALT = 0x6L;

  private final NoiseField corruption;
  private final NoiseField patch;
  private final NoiseField blight;
  private final NoiseField impact;
  private final NoiseField detail;

  /**
   * Builds the fields for one world.
   *
   * @param worldSeed the world seed
   * @param config the scales and octave count
   */
  public NoiseFields(long worldSeed, NoiseConfig config) {
    int octaves = config.getOctaves();
    this.corruption = new NoiseField(worldSeed, CORRUPTION_SALT, config.getCorruptionScale(),
        octaves);
    this.patch = new NoiseField(worldSeed, PATCH_SALT, config.getPatchScale(), octaves);
    this.blight = new NoiseField(worldSeed, BLIGHT_SALT, config.getBlightScale(), octaves);
    this.impact = new NoiseField(worldSeed, IMPACT_SALT, config.getImpactScale(), octaves);
    // Detail is deliberately single octave and small: it only picks between materials inside a
    // patch, and extra octaves there would add cost without changing how anything reads.
    this.detail = new NoiseField(worldSeed, DETAIL_SALT, config.getDetailScale(), 1);
  }

  /**
   * Returns the broad field that decides a chunk's corruption level.
   *
   * @return the corruption field
   */
  public NoiseField corruption() {
    return corruption;
  }

  /**
   * Returns the field that shapes irregular ground patches.
   *
   * @return the patch field
   */
  public NoiseField patch() {
    return patch;
  }

  /**
   * Returns the field that groups tree damage, so whole stands of trees die together.
   *
   * @return the blight field
   */
  public NoiseField blight() {
    return blight;
  }

  /**
   * Returns the field that marks impact zones where craters cluster.
   *
   * @return the impact field
   */
  public NoiseField impact() {
    return impact;
  }

  /**
   * Returns the fine field used to pick between materials within a patch.
   *
   * @return the detail field
   */
  public NoiseField detail() {
    return detail;
  }
}
