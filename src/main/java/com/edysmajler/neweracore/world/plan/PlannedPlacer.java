package com.edysmajler.neweracore.world.plan;

import com.edysmajler.neweracore.config.PlanConfig;
import com.edysmajler.neweracore.plan.LocationType;
import com.edysmajler.neweracore.plan.PlannedLocation;
import com.edysmajler.neweracore.plan.WorldPlan;
import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ChunkProcessor;
import com.edysmajler.neweracore.world.structures.StructureDefinition;
import com.edysmajler.neweracore.world.structures.StructureField;
import com.edysmajler.neweracore.world.structures.StructureManager;
import com.edysmajler.neweracore.world.structures.StructureSite;
import com.edysmajler.neweracore.world.towns.PlacedMarker;
import com.edysmajler.neweracore.world.towns.TownPlacer;
import com.edysmajler.neweracore.world.towns.TownSite;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.World;

/**
 * Builds what a designer put in the plan, as its ground finishes generating.
 *
 * <p>The same discipline as the two seeded placers, because the problem is identical: a settlement
 * spans dozens of chunks, so every chunk it touches asks whether the footprint is complete, the
 * placement is marked <em>before</em> anything is built, and the last few stragglers are generated
 * here rather than waiting for a player to sweep across the far corner. What differs is only where
 * the site came from — a file somebody authored instead of a hash of the seed.
 *
 * <p>A planned type with no builder places nothing and says so once. That is the honest behaviour
 * while the structure library is thin: approximating a hospital with whichever wreck happened to be
 * registered would put a lie on the ground at a coordinate the designer chose deliberately, and it
 * would be indistinguishable from the feature working.
 */
public final class PlannedPlacer implements ChunkProcessor {

  /**
   * How many ungenerated footprint chunks this will generate itself to finish a placement.
   *
   * <p>Higher than the seeded placers' budget because a planned settlement is a deliberate
   * destination: somebody will fly to it on purpose, and finding half a town because the far corner
   * had not generated is worse than one tick of work.
   */
  private static final int COMPLETION_BUDGET = 12;

  private final WorldPlanBook book;
  private final PlanConfig config;
  private final StructureManager structures;
  private final TownPlacer towns;
  private final PlacedMarker marker;
  private final Logger logger;
  private final Set<String> reported = ConcurrentHashMap.newKeySet();

  /**
   * Creates the placer.
   *
   * @param book the loaded plans, one per world
   * @param config the plan settings, for the type-to-structure mapping
   * @param structures the registry of what can be built
   * @param towns the town placer, whose layout builds planned settlements
   * @param marker the placed marker, which keeps each placement single-shot
   * @param logger the logger that reports each placement and each type it cannot build
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The registry, the town placer and the logger are shared collaborators held "
          + "by the whole pipeline; copying them is neither possible nor meaningful."
  )
  public PlannedPlacer(
      WorldPlanBook book,
      PlanConfig config,
      StructureManager structures,
      TownPlacer towns,
      PlacedMarker marker,
      Logger logger
  ) {
    this.book = book;
    this.config = config;
    this.structures = structures;
    this.towns = towns;
    this.marker = marker;
    this.logger = logger;
  }

  @Override
  public String name() {
    return "plan";
  }

  @Override
  public void process(ChunkContext context) {
    World world = context.world();
    WorldPlan plan = book.forWorld(world.getName(), world.getSeed());

    if (plan.locations().isEmpty()) {
      return;
    }

    for (PlannedLocation location
        : PlanSites.touching(plan.locations(), context.chunkX(), context.chunkZ())) {
      build(world, plan, location);
    }
  }

  /**
   * Builds every planned location in a world whose ground is already there.
   *
   * <p>The chunk trigger cannot cover the one place a designer is most likely to build: on a fresh
   * world the spawn area generates <em>before</em> plugins enable, so those chunks are never new to
   * the engine and nothing near the origin ever fires. Since the planner centres its map on 0,0,
   * that is precisely where the first town gets put — and it would silently never appear.
   *
   * <p>Also what makes a plan applicable to a world that already exists: a location standing on
   * long-generated terrain is built on the spot rather than waiting for a chunk that will never be
   * new again. The mark keeps it single-shot, so this is safe to run at every startup.
   *
   * @param world the world to catch up
   */
  public void sweep(World world) {
    WorldPlan plan = book.forWorld(world.getName(), world.getSeed());

    for (PlannedLocation location : plan.locations()) {
      build(world, plan, location);
    }
  }

  private void build(World world, WorldPlan plan, PlannedLocation location) {
    Builder builder = builderFor(location);
    if (builder == null) {
      return;
    }

    List<long[]> missing = missingFootprint(world, location, builder.radius());
    if (missing.size() > COMPLETION_BUDGET
        || marker.isPlaced(world, location.blockX(), location.blockZ())) {
      return;
    }

    // Mark first, exactly as the seeded placers do: the stragglers below run their own pipelines
    // re-entrantly — this processor included — and a failing build must not leave the placement
    // eligible for a second, compounding attempt.
    marker.markPlaced(world, location.blockX(), location.blockZ());

    for (long[] chunk : missing) {
      world.getChunkAt((int) chunk[0], (int) chunk[1]);
    }

    builder.build(world, plan, location);

    logger.info(() -> "Placed planned " + location.type().label().toLowerCase(Locale.ROOT)
        + " \"" + location.name() + "\" at " + location.blockX() + ", " + location.blockZ());
  }

  /**
   * Returns how to build a planned location, or null when nothing can build it yet.
   */
  private Builder builderFor(PlannedLocation location) {
    if (isSettlement(location.type())) {
      return new Builder(location.radius(), (world, plan, planned) -> {
        TownSite site = PlanSites.townSiteFor(plan, planned, world.getSeed());
        towns.build(new StructureField(world, site.seed()), site);
      });
    }

    String structureId = config.builderFor(location.type());
    if (structureId == null) {
      reportOnce(location, "no builder is configured for this type");
      return null;
    }

    Optional<StructureDefinition> definition = structures.byId(structureId);
    if (definition.isEmpty()) {
      reportOnce(location, "its builder \"" + structureId + "\" is not a registered structure");
      return null;
    }

    StructureDefinition structure = definition.get();

    // The structure's own radius decides the footprint, not the designer's: only the definition
    // knows how far its silhouette reaches, and an understated footprint leaves a chunk out of the
    // trigger and the wreck unbuilt. The planned radius still governs the ground it reserves.
    return new Builder(Math.max(location.radius(), structure.radius()), (world, plan, planned) -> {
      StructureSite site = PlanSites.structureSiteFor(
          planned, structureId, structure.radius(), world.getSeed());
      structure.place(new StructureField(world, site), site);
    });
  }

  private void reportOnce(PlannedLocation location, String because) {
    if (reported.add(location.id())) {
      logger.warning(() -> "Planned " + location.type().name() + " \"" + location.name()
          + "\" at " + location.blockX() + ", " + location.blockZ()
          + " was not built: " + because + ".");
    }
  }

  /**
   * Returns whether a planned type is built by the town system rather than by a structure.
   *
   * @param type the planned type
   * @return true for settlements
   */
  public static boolean isSettlement(LocationType type) {
    return type == LocationType.TOWN || type == LocationType.CITY;
  }

  private static List<long[]> missingFootprint(World world, PlannedLocation location, int radius) {
    int minX = (location.blockX() - radius) >> 4;
    int maxX = (location.blockX() + radius) >> 4;
    int minZ = (location.blockZ() - radius) >> 4;
    int maxZ = (location.blockZ() + radius) >> 4;

    List<long[]> missing = new ArrayList<>();

    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        if (!world.isChunkGenerated(x, z)) {
          missing.add(new long[] {x, z});

          if (missing.size() > COMPLETION_BUDGET) {
            return missing;
          }
        }
      }
    }

    return missing;
  }

  /** How one planned location gets built, and how far its footprint reaches. */
  private record Builder(int radius, Build build) {

    void build(World world, WorldPlan plan, PlannedLocation location) {
      build.run(world, plan, location);
    }
  }

  /** The build step itself, so the footprint check can happen before anything is written. */
  private interface Build {
    void run(World world, WorldPlan plan, PlannedLocation location);
  }
}
