package com.edysmajler.neweracore.world;

import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.corruption.CorruptionZone;
import com.edysmajler.neweracore.world.feature.CraterSites;
import com.edysmajler.neweracore.world.noise.NoiseFields;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Runs the corruption pipeline over each chunk exactly once, when it first generates.
 *
 * <p>Two independent gates keep the work single-shot. {@link ChunkLoadEvent#isNewChunk()} restricts
 * the engine to freshly generated terrain, so chunks that existed before the plugin — and anything
 * players have built — are never touched. {@link ChunkMarker} then records the engine version in
 * the
 * chunk itself, so even if a chunk is reported as new twice it is only transformed once.
 *
 * <p>Noise fields are built per world and cached: they depend only on the world seed, and
 * rebuilding
 * their permutation tables per chunk would be pure waste.
 */
public class WorldEngine implements Listener {

  private final WorldEngineConfig config;
  private final ChunkMarker marker;
  private final List<ChunkProcessor> pipeline;
  private final Logger logger;
  private final Map<UUID, NoiseFields> fieldsByWorld = new ConcurrentHashMap<>();

  /**
   * Creates the engine.
   *
   * @param config the world engine settings
   * @param marker the transformed-chunk marker
   * @param pipeline the processors to run, in order
   * @param logger the logger used to report processor failures
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The plugin's logger is shared by design; copying it is not possible."
  )
  public WorldEngine(
      WorldEngineConfig config,
      ChunkMarker marker,
      List<ChunkProcessor> pipeline,
      Logger logger
  ) {
    this.config = config;
    this.marker = marker;
    this.pipeline = List.copyOf(pipeline);
    this.logger = logger;
  }

  /**
   * Corrupts a chunk the first time it generates.
   *
   * @param event the chunk load event
   */
  @EventHandler
  public void onChunkLoad(ChunkLoadEvent event) {
    if (!config.isEnabled() || !event.isNewChunk()) {
      return;
    }

    Chunk chunk = event.getChunk();
    if (marker.isTransformed(chunk)) {
      return;
    }

    // Mark first: a processor that throws must not leave the chunk eligible for a second pass,
    // which would stack transformations on top of each other.
    marker.markTransformed(chunk);

    run(chunk, newContext(chunk));
  }

  private ChunkContext newContext(Chunk chunk) {
    NoiseFields fields = fieldsFor(chunk.getWorld());
    CorruptionZone zone = CorruptionZone.resolve(
        fields,
        config.getThresholds(),
        config.getLevels(),
        chunk.getX(),
        chunk.getZ()
    );

    return new ChunkContext(
        chunk,
        config,
        fields,
        zone,
        CraterSites.near(
            config.getHugeCraters(),
            config.getThresholds(),
            config.getLevels(),
            fields,
            chunk.getWorld().getSeed(),
            chunk.getX(),
            chunk.getZ(),
            1.35
        )
    );
  }

  private NoiseFields fieldsFor(World world) {
    return fieldsByWorld.computeIfAbsent(
        world.getUID(),
        unused -> new NoiseFields(world.getSeed(), config.getNoise())
    );
  }

  private void run(Chunk chunk, ChunkContext context) {
    for (ChunkProcessor processor : pipeline) {
      try {
        processor.process(context);
      } catch (RuntimeException e) {
        logger.log(
            Level.SEVERE,
            "World engine processor " + processor.name() + " failed at chunk "
                + chunk.getX() + ", " + chunk.getZ(),
            e
        );
      }
    }
  }
}
