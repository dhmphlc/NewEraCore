package com.edysmajler.neweracore.world.history;

import com.edysmajler.neweracore.config.HistoryConfig;
import com.edysmajler.neweracore.config.LandmarkConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the landmarks are.
 *
 * <p>Sites cannot be rolled per chunk. A landmark is wider than a chunk and matters to chunks that
 * are nowhere near it — a road heading for it, a settlement built in its lee — so every part of the
 * world has to be able to work out where it is without loading anything. So sites live on a coarse
 * grid in world coordinates and are derived by hashing the cell, which makes them a pure function
 * of the world seed, agreed on by every system that asks. This is the same trick huge craters use,
 * for the same reason.
 *
 * <p>Two details keep the grid from reading as a grid. Each site wanders inside its cell, but only
 * within the middle of it, so two sites either side of a border can never end up neighbours. And
 * some cells hold nothing, which turns a regular lattice into a spread of distances.
 *
 * <p>The type is chosen from two questions about the site, never from a bare roll. Its
 * <em>story</em> decides whether the place belongs in this region — a silo in a war zone, not in a
 * valley the fighting never reached. Its <em>ground</em> decides whether the place could have been
 * built here at all: a hydroelectric dam wants a river to hold back, and one standing on dry flat
 * land in the middle of nowhere is worse than no landmark, because it tells the player nothing was
 * thought about.
 *
 * <p>Both questions are answered from the world generator rather than from loaded chunks, so a
 * landmark stays knowable from arbitrarily far away — which is what lets a road aim at one from two
 * thousand blocks off.
 *
 * <p>A resolved cell is <strong>remembered</strong>, which is the one piece of mutable state in
 * this package and needs justifying. Every question about a position walks the nine cells around
 * it, so a chunk that asks about its own centre asks about nine cells, and the next chunk along
 * asks about most of the same nine. Asking the ground is not free — it is a question to the world
 * generator per sampled point — so without this the same cell is resolved from scratch for every
 * chunk in it, and a cell is 1500 blocks across: getting on for nine thousand chunks, each
 * repeating work whose answer cannot have changed.
 *
 * <p>It is safe because it is a memo and not state: the value for a cell is a pure function of the
 * seed, so any thread that computes it computes the same answer, and two threads racing to fill the
 * same key simply agree. The map is bounded and dropped wholesale when it fills, because a memo
 * that grows without limit as a player explores is a leak, and losing one is free — the answer is
 * recomputed, never wrong.
 */
public final class LandmarkMap {

  private static final long SITE_SALT = 0x1A4DBA12L;

  /**
   * How many resolved cells to remember. At the validated spacing this is a region some ninety
   * thousand blocks across, so in practice a session never reaches it.
   */
  private static final int REMEMBERED_CELLS = 4096;

  private final long worldSeed;
  private final LandmarkConfig config;
  private final HistoryConfig history;
  private final HistoryMaps maps;
  private final SiteTerrain terrain;
  private final Map<Long, Optional<Landmark>> resolved = new ConcurrentHashMap<>();

  /**
   * Builds the map for one world.
   *
   * @param worldSeed the world seed
   * @param history the history settings
   * @param maps the world's history layers, used to pick a type that suits the place
   * @param terrain what the ground is like, so a dam is not built where there is no water
   */
  public LandmarkMap(
      long worldSeed,
      HistoryConfig history,
      HistoryMaps maps,
      SiteTerrain terrain
  ) {
    this.worldSeed = worldSeed;
    this.config = history.getLandmarks();
    this.history = history;
    this.maps = maps;
    this.terrain = terrain;
  }

  /**
   * Returns every site in the nine grid cells around a position.
   *
   * <p>Nine cells is enough for any question about the immediate neighbourhood: with the validated
   * spacing, nothing outside them can be the nearest site to this position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the sites, in grid order
   */
  public List<Landmark> near(int blockX, int blockZ) {
    int spacing = config.getSpacing();
    int cellX = Math.floorDiv(blockX, spacing);
    int cellZ = Math.floorDiv(blockZ, spacing);

    List<Landmark> found = new ArrayList<>();

    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        siteIn(cellX + dx, cellZ + dz).ifPresent(found::add);
      }
    }

    return found;
  }

  /**
   * Returns the site whose footprint covers a position, if any.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the site the position stands on
   */
  public Optional<Landmark> covering(int blockX, int blockZ) {
    return coveringIn(near(blockX, blockZ), blockX, blockZ);
  }

  /**
   * Returns the site covering a position, out of sites already found.
   *
   * <p>For a caller that needs both this and {@link #nearestIn} and would otherwise walk the same
   * nine cells twice — which is the whole per-chunk cost of the landmark layer.
   *
   * @param sites the candidate sites
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the site the position stands on
   */
  public static Optional<Landmark> coveringIn(List<Landmark> sites, int blockX, int blockZ) {
    return sites.stream()
        .filter(landmark -> landmark.covers(blockX, blockZ))
        .findFirst();
  }

  /**
   * Returns the nearest site out of sites already found.
   *
   * @param sites the candidate sites
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the nearest site
   */
  public static Optional<Landmark> nearestIn(List<Landmark> sites, int blockX, int blockZ) {
    return sites.stream()
        .min((left, right) -> Double.compare(
            left.distanceTo(blockX, blockZ),
            right.distanceTo(blockX, blockZ)
        ));
  }

  /**
   * Returns the nearest site to a position.
   *
   * <p>What a road module wants: something to aim at, from wherever it happens to be.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the nearest site, empty only if all nine surrounding cells are vacant
   */
  public Optional<Landmark> nearest(int blockX, int blockZ) {
    return nearestIn(near(blockX, blockZ), blockX, blockZ);
  }

  /**
   * Returns the site in one grid cell, or empty when that cell holds none.
   *
   * @param cellX grid cell x
   * @param cellZ grid cell z
   * @return the site
   */
  public Optional<Landmark> siteIn(int cellX, int cellZ) {
    long key = ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    Optional<Landmark> remembered = resolved.get(key);

    if (remembered != null) {
      return remembered;
    }

    Optional<Landmark> site = resolveSiteIn(cellX, cellZ);

    if (resolved.size() >= REMEMBERED_CELLS) {
      // Dropping the lot beats evicting cleverly: every entry is recomputable, and the alternative
      // is bookkeeping about which cell a player might walk back into.
      resolved.clear();
    }

    resolved.put(key, site);

    return site;
  }

  private Optional<Landmark> resolveSiteIn(int cellX, int cellZ) {
    long hash = mix(worldSeed ^ SITE_SALT, cellX, cellZ);

    if (unitFrom(hash) >= config.getChance()) {
      return Optional.empty();
    }

    int spacing = config.getSpacing();
    double jitter = config.getJitter();
    double margin = (1.0 - jitter) / 2.0;

    long positionHash = mix(hash, 0x9E37L, 0x85EBL);
    int centerX = cellX * spacing
        + (int) ((margin + unitFrom(positionHash) * jitter) * spacing);
    int centerZ = cellZ * spacing
        + (int) ((margin + unitFrom(mix(positionHash, 1, 1)) * jitter) * spacing);

    LandmarkType type = typeAt(centerX, centerZ, hash);

    return type == null
        ? Optional.empty()
        : Optional.of(new Landmark(type, centerX, centerZ));
  }

  /**
   * Picks a type that suits both what happened at the site and what the ground there is.
   *
   * <p>Returns null when nothing could stand here — out at sea, most obviously. An empty cell is
   * the honest answer; forcing a type onto ground that cannot hold it is what put a hospital in an
   * ocean.
   */
  private LandmarkType typeAt(int centerX, int centerZ, long hash) {
    // Dry ground is the same answer for every candidate type, so it is asked once here. Left inside
    // the loop it was asked once per type — seven identical questions to the generator about one
    // block — and it also means a site at sea costs one question instead of resolving a story and a
    // full candidate list on the way to placing nothing.
    if (!terrain.isDryLand(centerX, centerZ)) {
      return null;
    }

    RegionStory story = RegionStory.of(
        history,
        maps.war().at(centerX, centerZ),
        maps.ashfall().at(centerX, centerZ),
        maps.restoration().at(centerX, centerZ)
    );

    List<LandmarkType> candidates = new ArrayList<>();
    for (LandmarkType type : LandmarkType.fitting(story)) {
      if (type.suitsGround(terrain, centerX, centerZ)) {
        candidates.add(type);
      }
    }

    if (candidates.isEmpty()) {
      return null;
    }

    int index = (int) (unitFrom(mix(hash, 0xC0FFEEL, 0xBEEFL)) * candidates.size());

    return candidates.get(Math.min(index, candidates.size() - 1));
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
