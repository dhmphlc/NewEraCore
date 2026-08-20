package com.edysmajler.neweracore.world.roads;

import com.edysmajler.neweracore.config.RoadsConfig;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The pre-war road network, derived entirely from the seed.
 *
 * <p>Two node grids, the {@code StructureSites} pattern stretched into lines. Towns sit on the
 * dense grid and local roads connect neighbouring towns, so a road always leads somewhere and a
 * town is never roadless — one system, not two coincidences. Highways run on their own sparse
 * grid, ignoring towns the way a motorway ignores villages. Each node connects to the nearest
 * node east and south of it within {@link #MAX_SPAN_CELLS} cells, which knits long unbroken
 * stretches without ever needing to know more than a few cells — the property that keeps every
 * query a pure function of the seed.
 *
 * <p>Roads deliberately do <em>not</em> pathfind. The published road mods route with A* over real
 * terrain, which needs the terrain loaded and the result stored; here every chunk must agree on
 * the network without loading anything, so segments are straight lines bent by seeded offsets, and
 * the paving pass simply declines to pave water and lets the land have its say column by column.
 * A road that dives into a lake and climbs out the far bank reads as a flooded road, which in this
 * world is the truth anyway.
 *
 * <p>Cars are sampled along each segment's arc, so a wreck is exactly as fixed as the road it
 * stopped on. Nothing here touches Bukkit except {@link LandLookup}, behind its seam.
 */
public final class RoadNetwork {

  /** How far a town's buildings can reach from its node. */
  public static final int TOWN_RADIUS = 48;

  /** How far past the centreline a road's influence reaches: paving, shoulders, poles, arms. */
  public static final double INFLUENCE = 10.0;

  /** How many cells east or south a node looks for its neighbour. */
  static final int MAX_SPAN_CELLS = 2;

  /** Arc step between car candidates along a segment. */
  private static final double CAR_STEP = 16.0;

  private static final long TOWN_SALT = 0x70A4B215L;
  private static final long HIGHWAY_SALT = 0x41C4B3E7L;
  private static final double TOWN_JITTER = 0.35;
  private static final double HIGHWAY_JITTER = 0.15;

  private record Node(int x, int z, long hash) {}

  private RoadNetwork() {}

  /**
   * Resolves everything the network puts near one chunk.
   *
   * @param config the road network settings
   * @param land what the world generator puts at a position, land or open water
   * @param worldSeed the world seed
   * @param chunkX chunk x coordinate
   * @param chunkZ chunk z coordinate
   * @return the chunk's road plan, {@link RoadPlan#EMPTY} when roads are off or nothing is near
   */
  public static RoadPlan near(
      RoadsConfig config,
      LandLookup land,
      long worldSeed,
      int chunkX,
      int chunkZ
  ) {
    if (!config.isEnabled()) {
      return RoadPlan.EMPTY;
    }

    int minX = chunkX * 16;
    int minZ = chunkZ * 16;

    List<RoadSegment> segments = new ArrayList<>();
    collectSegments(config, land, worldSeed, RoadKind.LOCAL, minX, minZ, segments);
    collectSegments(config, land, worldSeed, RoadKind.HIGHWAY, minX, minZ, segments);

    List<TownSite> towns = new ArrayList<>();
    collectTowns(config, land, worldSeed, chunkX, chunkZ, towns);

    List<CarSite> cars = new ArrayList<>();
    for (RoadSegment segment : segments) {
      carsAlong(config, segment, chunkX, chunkZ, cars);
    }

    if (segments.isEmpty() && towns.isEmpty()) {
      return RoadPlan.EMPTY;
    }

    return new RoadPlan(segments, towns, cars);
  }

  /**
   * Returns every town within a radius of a position, nearest first.
   *
   * <p>The locate-command view of the network: pure arithmetic over the seed, so it can answer
   * about ground that has never been generated.
   *
   * @param config the road network settings
   * @param land what the world generator puts at a position
   * @param worldSeed the world seed
   * @param blockX absolute block x to search around
   * @param blockZ absolute block z to search around
   * @param blockRadius how far to look, in blocks
   * @return the towns found, ordered by distance
   */
  public static List<TownSite> townsAround(
      RoadsConfig config,
      LandLookup land,
      long worldSeed,
      int blockX,
      int blockZ,
      int blockRadius
  ) {
    if (!config.isEnabled()) {
      return List.of();
    }

    int spacing = config.getTownSpacing();
    int cells = (int) Math.ceil(blockRadius / (double) spacing);
    int cellX = Math.floorDiv(blockX, spacing);
    int cellZ = Math.floorDiv(blockZ, spacing);

    List<TownSite> found = new ArrayList<>();

    for (int dx = -cells; dx <= cells; dx++) {
      for (int dz = -cells; dz <= cells; dz++) {
        townAt(config, land, worldSeed, cellX + dx, cellZ + dz)
            .filter(town -> town.distanceTo(blockX, blockZ) <= blockRadius)
            .ifPresent(found::add);
      }
    }

    found.sort(Comparator.comparingDouble(town -> town.distanceTo(blockX, blockZ)));
    return found;
  }

  /**
   * Collects the segments of one kind whose influence could reach a chunk.
   *
   * <p>The window is derived, not guessed: a segment's origin sits at most {@code MAX_SPAN_CELLS}
   * cells before the chunk's cell along its axis, and its bend can push it under a cell sideways,
   * so scanning one cell beyond the span in every direction covers every segment that could touch
   * — the bounding-box test then discards the rest.
   */
  private static void collectSegments(
      RoadsConfig config,
      LandLookup land,
      long worldSeed,
      RoadKind kind,
      int blockMinX,
      int blockMinZ,
      List<RoadSegment> out
  ) {
    int spacing = spacingOf(config, kind);
    int cellX = Math.floorDiv(blockMinX, spacing);
    int cellZ = Math.floorDiv(blockMinZ, spacing);
    int window = MAX_SPAN_CELLS + 1;

    for (int dx = -window; dx <= window; dx++) {
      for (int dz = -window; dz <= window; dz++) {
        connectionsFrom(config, land, worldSeed, kind, cellX + dx, cellZ + dz, segment -> {
          if (segment.nearBox(blockMinX, blockMinZ, blockMinX + 15, blockMinZ + 15, INFLUENCE)) {
            out.add(segment);
          }
        });
      }
    }
  }

  /**
   * Feeds one cell's outgoing connections — east and south — to a consumer.
   */
  private static void connectionsFrom(
      RoadsConfig config,
      LandLookup land,
      long worldSeed,
      RoadKind kind,
      int cellX,
      int cellZ,
      java.util.function.Consumer<RoadSegment> consumer
  ) {
    Optional<Node> origin = nodeIn(config, land, worldSeed, kind, cellX, cellZ);
    if (origin.isEmpty()) {
      return;
    }

    nearestNode(config, land, worldSeed, kind, cellX, cellZ, 1, 0)
        .ifPresent(target -> consumer.accept(between(kind, origin.get(), target)));
    nearestNode(config, land, worldSeed, kind, cellX, cellZ, 0, 1)
        .ifPresent(target -> consumer.accept(between(kind, origin.get(), target)));
  }

  /**
   * Returns the nearest node along one axis within the span, so chains stay knitted across a
   * missing cell.
   */
  private static Optional<Node> nearestNode(
      RoadsConfig config,
      LandLookup land,
      long worldSeed,
      RoadKind kind,
      int cellX,
      int cellZ,
      int stepX,
      int stepZ
  ) {
    for (int k = 1; k <= MAX_SPAN_CELLS; k++) {
      Optional<Node> node =
          nodeIn(config, land, worldSeed, kind, cellX + stepX * k, cellZ + stepZ * k);
      if (node.isPresent()) {
        return node;
      }
    }

    return Optional.empty();
  }

  /**
   * Returns the node in one grid cell, or empty when the cell holds none.
   *
   * <p>A node needs land: a road to a node at sea is a road to nowhere, so the sea eats the node
   * and the network routes around the coast at the coarse scale of its cells.
   */
  private static Optional<Node> nodeIn(
      RoadsConfig config,
      LandLookup land,
      long worldSeed,
      RoadKind kind,
      int cellX,
      int cellZ
  ) {
    long salt = kind == RoadKind.HIGHWAY ? HIGHWAY_SALT : TOWN_SALT;
    double chance = kind == RoadKind.HIGHWAY ? config.getHighwayChance() : config.getTownChance();
    double jitter = kind == RoadKind.HIGHWAY ? HIGHWAY_JITTER : TOWN_JITTER;
    int spacing = spacingOf(config, kind);

    long hash = mix(worldSeed ^ salt, cellX, cellZ);
    if (unitFrom(hash) >= chance) {
      return Optional.empty();
    }

    long jitterHash = mix(hash, 0x9E37L, 0x85EBL);
    int x = cellX * spacing + (int) (spacing * (0.5 + jitter * (unitFrom(jitterHash) - 0.5)));
    int z = cellZ * spacing
        + (int) (spacing * (0.5 + jitter * (unitFrom(mix(jitterHash, 1, 1)) - 0.5)));

    if (!land.isLand(x, z)) {
      return Optional.empty();
    }

    return Optional.of(new Node(x, z, hash));
  }

  /**
   * Builds the bent polyline between two nodes.
   *
   * <p>Three interior points, each pushed off the straight line by a seeded share of the length.
   * Deterministic bends are what keep the road from reading as a ruler line without ever needing
   * to know the terrain.
   */
  private static RoadSegment between(RoadKind kind, Node a, Node b) {
    double[] xs = new double[5];
    double[] zs = new double[5];
    xs[0] = a.x();
    zs[0] = a.z();
    xs[4] = b.x();
    zs[4] = b.z();

    double dx = b.x() - (double) a.x();
    double dz = b.z() - (double) a.z();
    double length = Math.hypot(dx, dz);
    double amplitude = length * kind.bendShare();
    double perpX = length == 0.0 ? 0.0 : -dz / length;
    double perpZ = length == 0.0 ? 0.0 : dx / length;
    long seed = mix(a.hash(), b.x(), b.z());

    for (int i = 1; i <= 3; i++) {
      double t = i / 4.0;
      double offset = (unitFrom(mix(seed, i, 0x0BEDL)) - 0.5) * 2.0 * amplitude;
      xs[i] = a.x() + dx * t + perpX * offset;
      zs[i] = a.z() + dz * t + perpZ * offset;
    }

    return new RoadSegment(kind, seed, xs, zs);
  }

  /**
   * Collects the towns whose footprint touches a chunk.
   */
  private static void collectTowns(
      RoadsConfig config,
      LandLookup land,
      long worldSeed,
      int chunkX,
      int chunkZ,
      List<TownSite> out
  ) {
    int spacing = config.getTownSpacing();
    int cells = 1 + TOWN_RADIUS / spacing;
    int cellX = Math.floorDiv(chunkX * 16, spacing);
    int cellZ = Math.floorDiv(chunkZ * 16, spacing);

    for (int dx = -cells; dx <= cells; dx++) {
      for (int dz = -cells; dz <= cells; dz++) {
        townAt(config, land, worldSeed, cellX + dx, cellZ + dz)
            .filter(town -> town.touchesChunk(chunkX, chunkZ))
            .ifPresent(out::add);
      }
    }
  }

  /**
   * Resolves the town in one cell, with the directions its roads leave in.
   *
   * <p>Outgoing east and south come from this cell's own connections; incoming west and north are
   * the neighbours' connections that end here. The house rows are laid along these, so a town
   * grows along its actual roads rather than along compass axes it may not have.
   */
  private static Optional<TownSite> townAt(
      RoadsConfig config,
      LandLookup land,
      long worldSeed,
      int cellX,
      int cellZ
  ) {
    Optional<Node> maybeNode = nodeIn(config, land, worldSeed, RoadKind.LOCAL, cellX, cellZ);
    if (maybeNode.isEmpty()) {
      return Optional.empty();
    }

    Node node = maybeNode.get();
    List<TownSite.Heading> roads = new ArrayList<>();

    // Outgoing: this node's own east and south connections
    nearestNode(config, land, worldSeed, RoadKind.LOCAL, cellX, cellZ, 1, 0)
        .ifPresent(target -> roads.add(headingAtStart(node, target)));
    nearestNode(config, land, worldSeed, RoadKind.LOCAL, cellX, cellZ, 0, 1)
        .ifPresent(target -> roads.add(headingAtStart(node, target)));

    // Incoming: the nearest western and northern neighbours connect to their nearest node along
    // the axis, which is exactly this one whenever they exist within the span
    nearestNode(config, land, worldSeed, RoadKind.LOCAL, cellX, cellZ, -1, 0)
        .ifPresent(origin -> roads.add(headingAtStart(node, origin)));
    nearestNode(config, land, worldSeed, RoadKind.LOCAL, cellX, cellZ, 0, -1)
        .ifPresent(origin -> roads.add(headingAtStart(node, origin)));

    return Optional.of(new TownSite(
        node.x(),
        node.z(),
        TOWN_RADIUS,
        mix(node.hash(), 0x707171L, 0x4E4EL),
        List.copyOf(roads)
    ));
  }

  /**
   * Returns the direction a road leaves a node in, pointed at the far node.
   *
   * <p>The straight-line direction, not the bent polyline's first step: house rows follow it, and
   * a row angled at the road's average is right where a row angled at one bent quarter is not.
   */
  private static TownSite.Heading headingAtStart(Node from, Node towards) {
    double dx = towards.x() - (double) from.x();
    double dz = towards.z() - (double) from.z();
    double length = Math.hypot(dx, dz);

    return length == 0.0
        ? new TownSite.Heading(1.0, 0.0)
        : new TownSite.Heading(dx / length, dz / length);
  }

  /**
   * Samples the cars a segment strews near one chunk.
   */
  private static void carsAlong(
      RoadsConfig config,
      RoadSegment segment,
      int chunkX,
      int chunkZ,
      List<CarSite> out
  ) {
    int steps = (int) (segment.length() / CAR_STEP);
    double chance = Math.min(0.9, CAR_STEP / config.getCarSpacing());

    for (int i = 1; i < steps; i++) {
      long hash = mix(segment.seed(), 0xCA25L, i);
      if (unitFrom(hash) >= chance) {
        continue;
      }

      double arc = i * CAR_STEP + (unitFrom(mix(hash, 3, 5)) - 0.5) * 8.0;
      double[] point = segment.pointAt(arc);
      double[] direction = segment.directionAt(arc);

      // Off the centreline but on the paving, like a car that stopped in its lane
      double lateral = (unitFrom(mix(hash, 7, 11)) - 0.5)
          * 2.0 * (segment.kind().halfWidth() - 1.2);
      int x = (int) Math.round(point[0] - direction[1] * lateral);
      int z = (int) Math.round(point[1] + direction[0] * lateral);

      double heading;
      if (unitFrom(mix(hash, 13, 17)) < 0.12) {
        // Slewed right across the road: the panic stop
        heading = unitFrom(mix(hash, 19, 23)) * Math.PI * 2.0;
      } else {
        heading = Math.atan2(direction[1], direction[0])
            + (unitFrom(mix(hash, 29, 31)) - 0.5) * 0.5;
      }

      CarSite car = new CarSite(x, z, heading, mix(hash, x, z));
      if (car.touchesChunk(chunkX, chunkZ)) {
        out.add(car);
      }
    }
  }

  private static int spacingOf(RoadsConfig config, RoadKind kind) {
    return kind == RoadKind.HIGHWAY ? config.getHighwaySpacing() : config.getTownSpacing();
  }

  private static long mix(long seed, long x, long z) {
    long hash = seed;
    hash ^= x * 0x9E3779B97F4A7C15L;
    hash ^= z * 0xC2B2AE3D27D4EB4FL;
    hash ^= hash >>> 33;
    hash *= 0xFF51AFD7ED558CCDL;
    hash ^= hash >>> 33;
    hash *= 0xC4CEB9FE1A85EC53L;
    hash ^= hash >>> 33;
    return hash;
  }

  private static double unitFrom(long hash) {
    return (hash >>> 11) * 0x1.0p-53;
  }
}
