package com.edysmajler.neweracore.world;

import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.plan.PlannedLocation;
import com.edysmajler.neweracore.world.corruption.CorruptionZone;
import com.edysmajler.neweracore.world.feature.CraterSites;
import com.edysmajler.neweracore.world.noise.NoiseFields;
import com.edysmajler.neweracore.world.plan.PlanSites;
import com.edysmajler.neweracore.world.plan.PlannedPlacer;
import com.edysmajler.neweracore.world.plan.WorldPlanBook;
import com.edysmajler.neweracore.world.structures.StructureManager;
import com.edysmajler.neweracore.world.structures.StructureSite;
import com.edysmajler.neweracore.world.structures.StructureSites;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import com.edysmajler.neweracore.world.towns.TownSite;
import com.edysmajler.neweracore.world.towns.TownSites;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Runs the corruption pipeline over each chunk exactly once, when it first generates.
 *
 * <p>Two independent gates keep the work single-shot. {@link ChunkLoadEvent#isNewChunk()} restricts
 * the engine to freshly generated terrain, so chunks that existed before the plugin — and anything
 * players have built — are never touched. {@link ChunkMarker} then records the engine version in
 * the chunk itself, so even if a chunk is reported as new twice it is only transformed once.
 *
 * <p>Each world's {@link NoiseFields} are built once and cached: they depend only on the world
 * seed, and building them calibrates every field against thousands of samples, so rebuilding them
 * per chunk would be pure waste.
 */
public class WorldEngine implements Listener {

  private final WorldEngineConfig config;
  private final ChunkMarker marker;
  private final List<ChunkProcessor> pipeline;
  private final StructureManager structures;
  private final WorldPlanBook plans;
  private final PlannedPlacer plannedPlacer;
  private final Logger logger;
  private final Map<UUID, NoiseFields> fieldsByWorld = new ConcurrentHashMap<>();

  /**
   * Creates the engine.
   *
   * @param config the world engine settings
   * @param marker the transformed-chunk marker
   * @param pipeline the processors to run, in order
   * @param structures the registry of scattered structures
   * @param plans the hand-authored plans, one per world
   * @param plannedPlacer the placer for hand-authored locations, which also runs outside chunk load
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
      StructureManager structures,
      WorldPlanBook plans,
      PlannedPlacer plannedPlacer,
      Logger logger
  ) {
    this.config = config;
    this.marker = marker;
    this.pipeline = List.copyOf(pipeline);
    this.structures = structures;
    this.plans = plans;
    this.plannedPlacer = plannedPlacer;
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
   * Builds the planned locations in a world that is loading after the engine started.
   *
   * @param event the world load event
   */
  @EventHandler
  public void onWorldLoad(WorldLoadEvent event) {
    if (config.isEnabled()) {
      plannedPlacer.sweep(event.getWorld());
    }
  }

  /**
   * Builds the planned locations standing on ground that already exists.
   *
   * <p>Called once at enable, for the worlds that were loaded before the engine was. Without it the
   * spawn area is a blind spot: it generates before any plugin is enabled, so its chunks are never
   * new to the engine — and a planner that centres its map on 0,0 puts the designer's first town
   * right there.
   *
   * @param worlds the worlds already loaded
   */
  public void catchUpPlans(List<World> worlds) {
    if (!config.isEnabled()) {
      return;
    }

    for (World world : worlds) {
      plannedPlacer.sweep(world);
    }
  }

  /**
   * Returns a world's noise fields, building them on first use.
   *
   * <p>Public because the fields are worth asking about from outside chunk generation: commands
   * that report where things will be need the same calibrated instance the generator uses — a
   * second one would give the same answers, being a pure function of the seed, but would pay the
   * calibration cost again for nothing.
   *
   * @param world the world to ask about
   * @return that world's fields
   */
  public NoiseFields fields(World world) {
    return fieldsByWorld.computeIfAbsent(
        world.getUID(),
        unused -> new NoiseFields(world.getSeed(), config.getNoise())
    );
  }

  /**
   * Returns the registry of scattered structures.
   *
   * @return the structure manager
   */
  public StructureManager structures() {
    return structures;
  }

  /**
   * Returns the loaded plans.
   *
   * <p>Public for the same reason the fields and the registry are: a command that reports what the
   * world will contain has to read exactly what the generator reads.
   *
   * @return the plan book
   */
  public WorldPlanBook plans() {
    return plans;
  }

  /**
   * Returns what a world's generator puts at a position, land or open water.
   *
   * <p>Public because siting anything world-scale needs the same answer the generator will give,
   * and a command reporting where those things are has to agree with what will actually be built.
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
    World world = chunk.getWorld();
    NoiseFields fields = fields(world);
    LandLookup land = land(world);

    return new ChunkContext(
        chunk,
        config,
        fields,
        CorruptionZone.resolve(
            fields, config.getThresholds(), config.getLevels(), chunk.getX(), chunk.getZ()),
        CraterSites.near(
            config.getHugeCraters(),
            fields,
            config.getThresholds(),
            land,
            world.getSeed(),
            chunk.getX(),
            chunk.getZ(),
            1.35
        ),
        withoutPlannedGround(
            world,
            StructureSites.near(
                config.getStructures(),
                structures,
                land,
                world.getSeed(),
                chunk.getX(),
                chunk.getZ()
            ),
            StructureSite::centerX,
            StructureSite::centerZ
        ),
        withoutPlannedGround(
            world,
            TownSites.near(
                config.getTowns(),
                land,
                world.getSeed(),
                chunk.getX(),
                chunk.getZ()
            ),
            TownSite::centerX,
            TownSite::centerZ
        )
    );
  }

  /**
   * Drops the seeded sites standing on ground a designer has already claimed.
   *
   * <p>The plan wins where the two systems overlap. A procedural town a few blocks from a designed
   * one is worse than either alone: the designed one stops reading as deliberate, which was the
   * entire reason for placing it by hand. The seeded site is simply absent rather than moved,
   * because moving it would make every site's position depend on the plan and shift the whole
   * world the first time a marker was nudged.
   */
  private <T> List<T> withoutPlannedGround(
      World world,
      List<T> sites,
      ToIntFunction<T> centerX,
      ToIntFunction<T> centerZ
  ) {
    if (sites.isEmpty()) {
      return sites;
    }

    List<PlannedLocation> planned = plans.forWorld(world.getName(), world.getSeed()).locations();
    if (planned.isEmpty()) {
      return sites;
    }

    int clearance = config.getPlan().getClearance();
    List<T> kept = new ArrayList<>(sites.size());

    for (T site : sites) {
      if (!PlanSites.isReserved(
          planned, centerX.applyAsInt(site), centerZ.applyAsInt(site), clearance)) {
        kept.add(site);
      }
    }

    return kept;
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
