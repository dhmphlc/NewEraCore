package com.edysmajler.neweracore.world.towns;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ChunkProcessor;
import com.edysmajler.neweracore.world.structures.StructureField;
import com.edysmajler.neweracore.world.structures.loot.LootTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Builds each town the moment enough of its footprint has generated.
 *
 * <p>The structure placer's discipline at town scale: every chunk the footprint touches asks
 * whether the footprint is complete, the site is marked <em>before</em> building, and the last few
 * straggler chunks are generated on demand so a player circling the edge of a town does not leave
 * it forever unbuilt. Towns sit a thousand blocks apart, so completing one can never chain into
 * completing the next.
 *
 * <p>The houses line the streets leaving the centre, doors inward, because a town is a street with
 * buildings on it — buildings scattered around a point are a camp. Rows step outward along each
 * street with jittered setbacks, a claimed-ground check keeps two rows from colliding at the
 * crossing, and a dry well marks the square. Everything comes off the town's seed.
 */
public final class TownPlacer implements ChunkProcessor {

  /** How many ungenerated footprint chunks the placer will generate itself to finish a town. */
  private static final int COMPLETION_BUDGET = 10;

  /** Distances along each street where a house row can stand. */
  private static final int[] ROW_SLOTS = {14, 27, 40};

  /** Minimum spacing between two house centres. */
  private static final double HOUSE_SPACING = 13.0;

  private final PlacedMarker marker;
  private final LootTable houseLoot;
  private final Logger logger;

  /**
   * Creates the placer.
   *
   * @param marker the placed-town marker
   * @param houseLoot what a house cupboard holds, or null to leave them bare
   * @param logger the logger that reports each town
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The plugin's logger is shared by design; copying it is not possible."
  )
  public TownPlacer(PlacedMarker marker, LootTable houseLoot, Logger logger) {
    this.marker = marker;
    this.houseLoot = houseLoot;
    this.logger = logger;
  }

  @Override
  public String name() {
    return "towns";
  }

  @Override
  public void process(ChunkContext context) {
    World world = context.world();

    for (TownSite town : context.townSites()) {
      List<long[]> missing = missingFootprint(world, town);
      if (missing.size() > COMPLETION_BUDGET
          || marker.isPlaced(world, town.centerX(), town.centerZ())) {
        continue;
      }

      marker.markPlaced(world, town.centerX(), town.centerZ());

      // Each chunk generated here runs its own full pipeline re-entrantly — including this
      // placer, which the mark above turns away — so the town is built over finished terrain
      for (long[] chunk : missing) {
        world.getChunkAt((int) chunk[0], (int) chunk[1]);
      }

      build(new StructureField(world, town.seed()), town);
      logger.info(() -> "Placed town at " + town.centerX() + ", " + town.centerZ());
    }
  }

  /**
   * Lays a town out and builds it.
   *
   * <p>Public because a hand-authored plan builds its settlements through here too: the layout is
   * the tested code for putting a ruined town on the ground, and a plan-specific copy of it would
   * be a second implementation drifting from this one. The plan supplies a site like any other; the
   * only difference is where the site came from.
   *
   * @param field the world writer over the footprint
   * @param town where the town stands, how far it reaches, and which ways its streets run
   */
  public void build(StructureField field, TownSite town) {
    Random random = field.random();

    List<TownSite.Heading> streets = town.streets().isEmpty()
        // A town whose neighbours all came to nothing still gets its hamlet, on compass streets
        ? List.of(new TownSite.Heading(1.0, 0.0), new TownSite.Heading(0.0, 1.0))
        : town.streets();

    // Every slot a house could stand in: {which road, distance along it, which side}. Shuffled so
    // which slots stay empty varies per town instead of always trimming the far end of the street.
    List<int[]> slots = new ArrayList<>();
    for (int streetIndex = 0; streetIndex < streets.size(); streetIndex++) {
      for (int along : ROW_SLOTS) {
        for (int side = -1; side <= 1; side += 2) {
          slots.add(new int[] {streetIndex, along, side});
        }
      }
    }
    Collections.shuffle(slots, random);

    int target = 5 + random.nextInt(4);
    List<int[]> claimed = new ArrayList<>();

    for (int[] slot : slots) {
      if (claimed.size() >= target) {
        break;
      }

      TownSite.Heading street = streets.get(slot[0]);
      int along = slot[1];
      int side = slot[2];
      int setback = 8 + random.nextInt(3);

      int x = town.centerX()
          + (int) Math.round(street.x() * along - street.z() * setback * side);
      int z = town.centerZ()
          + (int) Math.round(street.z() * along + street.x() * setback * side);

      if (tooClose(claimed, x, z) || field.isFluidColumn(x, z)) {
        continue;
      }

      // The door looks from the house back across its setback, into the street
      int facing = quarterTowards(street.z() * side, -street.x() * side);

      RuinedHouse.build(field, x, z, facing, random.nextLong(), houseLoot);
      claimed.add(new int[] {x, z});
    }

    buildWell(field, random, town, streets.get(0));
  }

  /**
   * Sets the dry well down on the square.
   */
  private static void buildWell(
      StructureField field,
      Random random,
      TownSite town,
      TownSite.Heading street
  ) {
    if (random.nextDouble() >= 0.7) {
      return;
    }

    // Diagonally off the crossing, clear of the house rows
    int x = town.centerX() + (int) Math.round((street.x() - street.z()) * 8.0);
    int z = town.centerZ() + (int) Math.round((street.z() + street.x()) * 8.0);

    // Every column of the ring must be dry, not just the middle of it
    for (int du = -1; du <= 1; du++) {
      for (int dv = -1; dv <= 1; dv++) {
        if (field.isFluidColumn(x + du, z + dv)) {
          return;
        }
      }
    }

    int ground = field.groundY(x, z);

    for (int du = -1; du <= 1; du++) {
      for (int dv = -1; dv <= 1; dv++) {
        if (du == 0 && dv == 0) {
          // The shaft, long dry
          field.clear(x, ground, z);
          field.set(x, ground - 1, z, Material.AIR);
          field.set(x, ground - 2, z, Material.AIR);
          continue;
        }

        field.set(x + du, ground + 1, z + dv,
            random.nextDouble() < 0.75 ? Material.COBBLESTONE : Material.MOSSY_COBBLESTONE);
      }
    }
  }

  private static boolean tooClose(List<int[]> claimed, int x, int z) {
    for (int[] house : claimed) {
      if (Math.hypot(house[0] - (double) x, house[1] - (double) z) < HOUSE_SPACING) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the quarter rotation whose door wall faces closest to a direction.
   */
  private static int quarterTowards(double dx, double dz) {
    if (Math.abs(dx) >= Math.abs(dz)) {
      return dx >= 0 ? 0 : 2;
    }
    return dz >= 0 ? 1 : 3;
  }

  private List<long[]> missingFootprint(World world, TownSite town) {
    int minX = (town.centerX() - town.radius()) >> 4;
    int maxX = (town.centerX() + town.radius()) >> 4;
    int minZ = (town.centerZ() - town.radius()) >> 4;
    int maxZ = (town.centerZ() + town.radius()) >> 4;

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
}
