package com.edysmajler.neweracore.world;

import com.edysmajler.neweracore.world.noise.NoiseField;

/**
 * Noise samples for the columns of one chunk, computed once and reused.
 *
 * <p>Several features read the same field over the same 256 columns, so sampling on demand would
 * repeat the same octave sums many times per chunk. Each field is filled on first access and then
 * served from a flat array, which keeps a field that a level never uses — rubble in recovered land,
 * for example — from costing anything at all.
 */
public final class ColumnMasks {

  private final NoiseField field;
  private final int originX;
  private final int originZ;
  private final double[] values = new double[ChunkContext.CHUNK_SIZE * ChunkContext.CHUNK_SIZE];

  private boolean filled;

  /**
   * Creates a lazy mask over one chunk.
   *
   * @param field the field to sample
   * @param originX absolute block x of the chunk's corner
   * @param originZ absolute block z of the chunk's corner
   */
  public ColumnMasks(NoiseField field, int originX, int originZ) {
    this.field = field;
    this.originX = originX;
    this.originZ = originZ;
  }

  /**
   * Returns the field value for a column.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return the value between 0 and 1
   */
  public double at(int x, int z) {
    if (!filled) {
      fill();
    }

    return values[(x << 4) | z];
  }

  private void fill() {
    for (int x = 0; x < ChunkContext.CHUNK_SIZE; x++) {
      for (int z = 0; z < ChunkContext.CHUNK_SIZE; z++) {
        values[(x << 4) | z] = field.sample(originX + x, originZ + z);
      }
    }

    filled = true;
  }
}
