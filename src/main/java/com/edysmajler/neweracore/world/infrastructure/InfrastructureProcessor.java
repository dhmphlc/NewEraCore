package com.edysmajler.neweracore.world.infrastructure;

import com.edysmajler.neweracore.config.InfrastructureConfig;
import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ChunkProcessor;
import java.util.List;
import org.bukkit.Material;

/**
 * Draws the slice of the network that passes through one chunk.
 *
 * <p><strong>This runs first, before anything else in the pipeline</strong>, and the order is the
 * point. Build the roads and then let the world happen to them: the ash settles over the tarmac,
 * the trees that grew through it are cleared and the ones beside it die, and a crater takes a bite
 * out of a highway. Draw them last instead and you get a pristine road network laid neatly over a
 * ruined world, which reads as a mod rather than as a place.
 *
 * <p>Rasterising walks the route's samples and paints the columns around each one, rather than
 * asking each of the chunk's 256 columns how far it is from the whole curve. A route is thousands
 * of samples long; the first costs a few hundred tests per chunk, the second costs hundreds of
 * thousands.
 *
 * <p>Where two routes cross, the wider one wins the ground. Junctions are not built as junctions
 * yet — the roads simply overlap, which is legible enough at ground level and much less likely to
 * produce something broken than a half-designed interchange.
 */
public final class InfrastructureProcessor implements ChunkProcessor {

  /** How far past the chunk to look for routes: the widest route's half width, plus a margin. */
  private static final double CHUNK_REACH = 24.0;

  private static final int SIZE = ChunkContext.CHUNK_SIZE;

  private static final Material RUNWAY = Material.GRAY_CONCRETE;
  private static final Material MARKING = Material.LIGHT_GRAY_CONCRETE;
  private static final Material SHOULDER = Material.GRAVEL;

  @Override
  public String name() {
    return "infrastructure";
  }

  @Override
  public void process(ChunkContext context) {
    InfrastructureConfig config = context.getConfig().getInfrastructure();
    if (!config.isEnabled()) {
      return;
    }

    List<Route> routes = context.infrastructure()
        .routesNear(context.blockX(8), context.blockZ(8), CHUNK_REACH);

    if (routes.isEmpty()) {
      buildAirfields(context, config);
      return;
    }

    // One plan for the whole chunk first, so a column crossed by two routes is built once
    RouteType[] lane = new RouteType[SIZE * SIZE];
    boolean[] middle = new boolean[SIZE * SIZE];
    boolean[] edge = new boolean[SIZE * SIZE];
    boolean[] alongX = new boolean[SIZE * SIZE];

    for (Route route : routes) {
      plot(context, route, lane, middle, edge, alongX);
    }

    build(context, config, lane, middle, edge, alongX);
    buildAirfields(context, config);
  }

  /**
   * Cuts and fills a runway flat, and paves it.
   *
   * <p>Built after the roads, so a highway arriving at an airport stops at the perimeter instead of
   * running across the strip. Levelling is what makes it a runway at all: an aircraft cannot land
   * on a gradient, so here the ground gives way to the structure rather than the other way round.
   */
  private static void buildAirfields(ChunkContext context, InfrastructureConfig config) {
    List<Airfield> airfields = context.infrastructure().airfieldsNear(
        context.blockX(8), context.blockZ(8), CHUNK_REACH + config.getApproachReach());

    for (Airfield airfield : airfields) {
      for (int x = 0; x < SIZE; x++) {
        for (int z = 0; z < SIZE; z++) {
          buildAirfieldColumn(context, config, airfield, x, z);
        }
      }
    }
  }

  /**
   * Builds, or merely clears, one column of an airfield.
   */
  private static void buildAirfieldColumn(
      ChunkContext context,
      InfrastructureConfig config,
      Airfield airfield,
      int x,
      int z
  ) {
    int blockX = context.blockX(x);
    int blockZ = context.blockZ(z);

    if (airfield.covers(blockX, blockZ)) {
      context.reserve(x, z);

      if (!Earthworks.levelTo(context, x, z, airfield.platformY(), config.getClearance())) {
        // The far end ran out over ground too low to fill. Better a gap than a plinth.
        return;
      }

      context.set(x, airfield.platformY(), z, surfaceOf(airfield, blockX, blockZ));
      return;
    }

    // Off the built ground, but perhaps under the approach: take the top off anything in the way
    // and leave everything below it exactly as it was. A hill beside a runway is not a paving
    // problem.
    int ceiling = airfield.ceilingAt(
        blockX, blockZ, config.getApproachReach(), config.getApproachSlope());

    if (ceiling != Integer.MAX_VALUE && context.groundY(x, z) > ceiling) {
      Earthworks.clearAbove(context, x, z, ceiling);
    }
  }

  /**
   * Returns what one square of an airfield is made of.
   */
  private static Material surfaceOf(Airfield airfield, int blockX, int blockZ) {
    if (!airfield.isPaved(blockX, blockZ)) {
      return SHOULDER;
    }

    return airfield.isMarking(blockX, blockZ) ? MARKING : RUNWAY;
  }

  /**
   * Marks every column this route touches, walking its samples.
   */
  private static void plot(
      ChunkContext context,
      Route route,
      RouteType[] lane,
      boolean[] middle,
      boolean[] edge,
      boolean[] alongX
  ) {
    RoutePath path = route.path();
    RouteType type = route.type();
    // Which way the line runs here, so a wire can be laid along it rather than left dangling
    boolean runsEastWest = Math.abs(route.to().centerX() - route.from().centerX())
        >= Math.abs(route.to().centerZ() - route.from().centerZ());
    double half = type.halfWidth();
    int reach = (int) Math.ceil(half);

    for (int i = 0; i < path.sampleCount(); i++) {
      int sampleX = (int) Math.round(path.sampleX(i)) - context.blockX(0);
      int sampleZ = (int) Math.round(path.sampleZ(i)) - context.blockZ(0);

      if (sampleX < -reach || sampleX > SIZE + reach
          || sampleZ < -reach || sampleZ > SIZE + reach) {
        continue;
      }

      for (int x = sampleX - reach; x <= sampleX + reach; x++) {
        for (int z = sampleZ - reach; z <= sampleZ + reach; z++) {
          if (!context.inChunk(x, z)) {
            continue;
          }

          double distance = Math.hypot(
              context.blockX(x) - path.sampleX(i),
              context.blockZ(z) - path.sampleZ(i)
          );

          if (distance > half) {
            continue;
          }

          int index = (x << 4) | z;
          if (lane[index] == null || type.width() > lane[index].width()) {
            lane[index] = type;
          }

          edge[index] |= distance > half - 1.0;
          middle[index] |= distance <= 1.0;
          alongX[index] = runsEastWest;
        }
      }
    }
  }

  /**
   * Builds every marked column once.
   */
  private static void build(
      ChunkContext context,
      InfrastructureConfig config,
      RouteType[] lane,
      boolean[] middle,
      boolean[] edge,
      boolean[] alongX
  ) {
    for (int x = 0; x < SIZE; x++) {
      for (int z = 0; z < SIZE; z++) {
        int index = (x << 4) | z;
        RouteType type = lane[index];

        if (type == null) {
          continue;
        }

        if (Roadbed.claimsGround(type)) {
          // Tell the ashfall to lay its dust on this and not repave it
          context.reserve(x, z);
        }

        Roadbed.build(context, config, type, edge[index] && !middle[index], x, z);

        if (type == RouteType.POWER_LINE && middle[index]) {
          PowerLine.build(context, config, alongX[index], x, z);
        }
      }
    }
  }
}
