package com.edysmajler.neweracore.world;

/**
 * One stage of the world transformation pipeline.
 *
 * <p>Processors run in registration order against the same {@link ChunkContext}, so a later stage
 * observes the blocks earlier stages wrote. Future systems — radiation, restoration, ruins, loot,
 * custom structures — plug in by implementing this interface and being added to the engine's
 * pipeline, with no changes to the chunk gating or marking logic.
 */
public interface ChunkProcessor {

  /**
   * Returns a short name used in log messages.
   *
   * @return the processor name
   */
  String name();

  /**
   * Transforms a freshly generated chunk.
   *
   * @param context the chunk being transformed
   */
  void process(ChunkContext context);
}
