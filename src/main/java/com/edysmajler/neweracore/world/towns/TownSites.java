package com.edysmajler.neweracore.world.towns;

import com.edysmajler.neweracore.config.TownsConfig;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds the ruined towns near a chunk, derived entirely from the seed.
 *
 * <p>The {@code StructureSites} pattern on its own grid: a candidate cell hashes into a town or
 * nothing, the node needs land, and every chunk the footprint touches computes the same answer
 * without loading anything. Each town also looks for its nearest neighbour within
 * {@link #MAX_SPAN_CELLS} cells in the four cardinal directions — not to build anything between
 * them, but to know which ways its streets run, so the houses line up toward the next settlement
 * the way real villages grow along the way to somewhere.
 *
 * <p>This system once paved actual roads and strewed cars along them; both were removed by
 * decision — they read as clutter — and only the street <em>directions</em> survive, orienting
 * the towns.
 */
public final class TownSites {

  /** How far a town's buildings can reach from its node. */
  public static final int TOWN_RADIUS = 48;

  /** How many cells away a town looks for the neighbour its streets point at. */
  static final int MAX_SPAN_CELLS = 2;

  private static final long TOWN_SALT = 0x70A4B215L;
  private static final double JITTER = 0.35;

  private record Node(int x, int z, long hash) {}

  private TownSites() {}

  /**
   * Returns the towns whose footprint reaches into a chunk.
   *
   * @param config the town settings
   * @param land what the world generator puts at a position, land or open water
   * @param worldSeed the world seed
   * @param chunkX chunk x coordinate
   * @param chunkZ chunk z coordinate
   * @return the towns touching this chunk, usually empty
   */
  public static List<TownSite> near(
      TownsConfig config,
      LandLookup land,
      long worldSeed,
      int chunkX,
      int chunkZ
  ) {
    if (!config.isEnabled()) {
      return List.of();
    }

    int spacing = config.getSpacing();
    int cells = 1 + TOWN_RADIUS / spacing;
    int cellX = Math.floorDiv(chunkX * 16, spacing);
    int cellZ = Math.floorDiv(chunkZ * 16, spacing);

    List<TownSite> towns = new ArrayList<>();

    for (int dx = -cells; dx <= cells; dx++) {
      for (int dz = -cells; dz <= cells; dz++) {
        townAt(config, land, worldSeed, cellX + dx, cellZ + dz)
            .filter(town -> town.touchesChunk(chunkX, chunkZ))
            .ifPresent(towns::add);
      }
    }

    return towns;
  }

  /**
   * Returns every town within a radius of a position, nearest first.
   *
   * <p>The locate-command view: pure arithmetic over the seed, so it can answer about ground that
   * has never been generated.
   *
   * @param config the town settings
   * @param land what the world generator puts at a position
   * @param worldSeed the world seed
   * @param blockX absolute block x to search around
   * @param blockZ absolute block z to search around
   * @param blockRadius how far to look, in blocks
   * @return the towns found, ordered by distance
   */
  public static List<TownSite> around(
      TownsConfig config,
      LandLookup land,
      long worldSeed,
      int blockX,
      int blockZ,
      int blockRadius
  ) {
    if (!config.isEnabled()) {
      return List.of();
    }

    int spacing = config.getSpacing();
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
   * Resolves the town in one cell, streets pointed at its nearest neighbours.
   */
  private static Optional<TownSite> townAt(
      TownsConfig config,
      LandLookup land,
      long worldSeed,
      int cellX,
      int cellZ
  ) {
    Optional<Node> maybeNode = nodeIn(config, land, worldSeed, cellX, cellZ);
    if (maybeNode.isEmpty()) {
      return Optional.empty();
    }

    Node node = maybeNode.get();
    List<TownSite.Heading> streets = new ArrayList<>();

    addStreet(streets, node, nearestNode(config, land, worldSeed, cellX, cellZ, 1, 0));
    addStreet(streets, node, nearestNode(config, land, worldSeed, cellX, cellZ, 0, 1));
    addStreet(streets, node, nearestNode(config, land, worldSeed, cellX, cellZ, -1, 0));
    addStreet(streets, node, nearestNode(config, land, worldSeed, cellX, cellZ, 0, -1));

    return Optional.of(new TownSite(
        node.x(),
        node.z(),
        TOWN_RADIUS,
        mix(node.hash(), 0x707171L, 0x4E4EL),
        streets
    ));
  }

  /**
   * Adds the street toward a neighbour, when there is one.
   */
  private static void addStreet(
      List<TownSite.Heading> streets,
      Node from,
      Optional<Node> towards
  ) {
    towards.ifPresent(target -> {
      double dx = target.x() - (double) from.x();
      double dz = target.z() - (double) from.z();
      double length = Math.hypot(dx, dz);

      if (length > 0.0) {
        streets.add(new TownSite.Heading(dx / length, dz / length));
      }
    });
  }

  /**
   * Returns the nearest town node along one axis within the span.
   */
  private static Optional<Node> nearestNode(
      TownsConfig config,
      LandLookup land,
      long worldSeed,
      int cellX,
      int cellZ,
      int stepX,
      int stepZ
  ) {
    for (int k = 1; k <= MAX_SPAN_CELLS; k++) {
      Optional<Node> node = nodeIn(config, land, worldSeed, cellX + stepX * k, cellZ + stepZ * k);
      if (node.isPresent()) {
        return node;
      }
    }

    return Optional.empty();
  }

  /**
   * Returns the node in one grid cell, or empty when the cell holds none.
   *
   * <p>A node needs land: a town cannot stand at sea, so coastlines thin the map out.
   */
  private static Optional<Node> nodeIn(
      TownsConfig config,
      LandLookup land,
      long worldSeed,
      int cellX,
      int cellZ
  ) {
    long hash = mix(worldSeed ^ TOWN_SALT, cellX, cellZ);
    if (unitFrom(hash) >= config.getChance()) {
      return Optional.empty();
    }

    int spacing = config.getSpacing();
    long jitterHash = mix(hash, 0x9E37L, 0x85EBL);
    int x = cellX * spacing + (int) (spacing * (0.5 + JITTER * (unitFrom(jitterHash) - 0.5)));
    int z = cellZ * spacing
        + (int) (spacing * (0.5 + JITTER * (unitFrom(mix(jitterHash, 1, 1)) - 0.5)));

    if (!land.isLand(x, z)) {
      return Optional.empty();
    }

    return Optional.of(new Node(x, z, hash));
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
