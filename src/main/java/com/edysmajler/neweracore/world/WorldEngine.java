package com.edysmajler.neweracore.world;

import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.feature.CraterSites;
import com.edysmajler.neweracore.world.history.HistoryEngine;
import com.edysmajler.neweracore.world.infrastructure.InfrastructureEngine;
import com.edysmajler.neweracore.world.terrain.LandLookup;
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
 * the chunk itself, so even if a chunk is reported as new twice it is only transformed once.
 *
 * <p>The world's {@link HistoryEngine} is built per world and cached. It depends only on the world
 * seed, and building it calibrates every noise field it owns, so rebuilding one per chunk would be
 * pure waste. Each chunk then asks it a single question — what happened in this region — and hands
 * the answer to the pipeline.
 */
public class WorldEngine implements Listener {

  private final WorldEngineConfig config;
  private final ChunkMarker marker;
  private final List<ChunkProcessor> pipeline;
  private final Logger logger;
  private final Map<UUID, HistoryEngine> historyByWorld = new ConcurrentHashMap<>();
  private final Map<UUID, InfrastructureEngine> networkByWorld = new ConcurrentHashMap<>();

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

  /**
   * Returns the simulated history of a world, building it on first use.
   *
   * <p>Public because the history is worth asking about from outside chunk generation: a command
   * reporting what happened where a player is standing, and every future system that has to plan
   * beyond the chunk in front of it, need the same instance the generator used. Building a second
   * one would give the same answers — it is a pure function of the seed — but would pay the
   * calibration cost again for nothing.
   *
   * @param world the world to ask about
   * @return that world's history
   */
  public HistoryEngine history(World world) {
    return historyByWorld.computeIfAbsent(
        world.getUID(),
        unused -> new HistoryEngine(world.getSeed(), config, land(world).siteTerrain())
    );
  }

  /**
   * Returns the network of routes between a world's landmarks, building it on first use.
   *
   * <p>Public for the reason the whole layer exists: what gets built later has to be able to ask
   * where the roads are, and get the same answer the roads themselves were drawn from.
   *
   * @param world the world to ask about
   * @return that world's infrastructure
   */
  public InfrastructureEngine infrastructure(World world) {
    return networkByWorld.computeIfAbsent(
        world.getUID(),
        unused -> new InfrastructureEngine(
            history(world), config, world.getSeaLevel(), world.getSeed())
    );
  }

  /**
   * Returns what a world's generator puts at a position, land or open water.
   *
   * <p>Beside {@link #history}, and public for the same reason: siting anything world-scale needs
   * the same answer the generator will give, and a command reporting where those things are has to
   * agree with what will actually be built.
   *
   * @param world the world to ask about
   * @return the lookup
   */
  public LandLookup land(World world) {
    return LandLookup.of(world);
  }

  /**
   * Returns whether this engine has already transformed a chunk.
   *
   * <p>Worth exposing because "why is this chunk untouched?" has exactly one common answer — it
   * generated before the plugin was installed — and no way to check it in game otherwise.
   *
   * @param chunk the chunk to check
   * @return true when the chunk carries the engine's mark
   */
  public boolean hasTransformed(Chunk chunk) {
    return marker.isTransformed(chunk);
  }

  private ChunkContext newContext(Chunk chunk) {
    HistoryEngine history = history(chunk.getWorld());

    return new ChunkContext(
        chunk,
        config,
        history.fields(),
        history.atChunk(chunk.getX(), chunk.getZ()),
        infrastructure(chunk.getWorld()),
        CraterSites.near(
            config.getHugeCraters(),
            history,
            land(chunk.getWorld()),
            chunk.getWorld().getSeed(),
            chunk.getX(),
            chunk.getZ(),
            1.35
        )
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
