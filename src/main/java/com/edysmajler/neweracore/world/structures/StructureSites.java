package com.edysmajler.neweracore.world.structures;

import com.edysmajler.neweracore.config.StructuresConfig;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds the structure sites near a chunk.
 *
 * <p>A structure spans chunk borders, so it cannot be rolled per chunk: every chunk it touches has
 * to agree on exactly where it stands, what it is, and which way it faces. Sites therefore live on
 * a coarse grid in world coordinates and are derived by hashing the cell against the world seed —
 * a pure function, so any chunk (and any command) gets the same answer without loading anything.
 *
 * <p>The candidate window is derived from the registry's largest radius, never picked by eye. The
 * same trap was fatal to routes once: a window sized by intuition means a chunk near the edge of a
 * big footprint never considers the site, and silently nothing appears there.
 *
 * <p>A candidate cell that rolls a structure whose ground refuses it holds nothing — the draw is
 * not retried, because a retry would make the site's identity depend on the ground test, and every
 * refinement of that test would silently move every structure in every existing world.
 */
public final class StructureSites {

  private static final long SITE_SALT = 0x57EC7B12L;

  private StructureSites() {}

  /**
   * Returns the sites whose footprint reaches into a chunk.
   *
   * @param config the structure scatter settings
   * @param structures the registry of what can be placed
   * @param land what the world generator puts at a position, land or open water
   * @param worldSeed the world seed
   * @param chunkX chunk x coordinate
   * @param chunkZ chunk z coordinate
   * @return the sites touching this chunk, usually empty
   */
  public static List<StructureSite> near(
      StructuresConfig config,
      StructureManager structures,
      LandLookup land,
      long worldSeed,
      int chunkX,
      int chunkZ
  ) {
    if (!config.isEnabled() || structures.isEmpty()) {
      return List.of();
    }

    int spacing = config.getSpacing();
    // Derived, not guessed: a footprint can reach maxRadius past its own cell, so the window has
    // to cover every cell whose site could touch this chunk.
    int cells = 1 + structures.maxRadius() / spacing;
    int cellX = Math.floorDiv(chunkX * 16, spacing);
    int cellZ = Math.floorDiv(chunkZ * 16, spacing);

    List<StructureSite> sites = new ArrayList<>();

    for (int dx = -cells; dx <= cells; dx++) {
      for (int dz = -cells; dz <= cells; dz++) {
        siteIn(config, structures, land, worldSeed, cellX + dx, cellZ + dz)
            .filter(site -> site.touchesChunk(chunkX, chunkZ))
            .ifPresent(sites::add);
      }
    }

    return sites;
  }

  /**
   * Returns every site within a radius of a position, nearest first.
   *
   * <p>{@link #near} answers what a chunk takes part in; this answers "where are they", for a
   * player who wants to go and find one. Pure arithmetic over the seed, so it can be asked about
   * ground that has never been generated.
   *
   * @param config the structure scatter settings
   * @param structures the registry of what can be placed
   * @param land what the world generator puts at a position, land or open water
   * @param worldSeed the world seed
   * @param blockX absolute block x to search around
   * @param blockZ absolute block z to search around
   * @param blockRadius how far to look, in blocks
   * @return the sites found, ordered by distance
   */
  public static List<StructureSite> around(
      StructuresConfig config,
      StructureManager structures,
      LandLookup land,
      long worldSeed,
      int blockX,
      int blockZ,
      int blockRadius
  ) {
    if (structures.isEmpty()) {
      return List.of();
    }

    int spacing = config.getSpacing();
    int cells = (int) Math.ceil(blockRadius / (double) spacing);
    int cellX = Math.floorDiv(blockX, spacing);
    int cellZ = Math.floorDiv(blockZ, spacing);

    List<StructureSite> found = new ArrayList<>();

    for (int dx = -cells; dx <= cells; dx++) {
      for (int dz = -cells; dz <= cells; dz++) {
        siteIn(config, structures, land, worldSeed, cellX + dx, cellZ + dz)
            .filter(site -> site.distanceTo(blockX, blockZ) <= blockRadius)
            .ifPresent(found::add);
      }
    }

    found.sort(Comparator.comparingDouble(site -> site.distanceTo(blockX, blockZ)));
    return found;
  }

  /**
   * Returns the site in one grid cell, or empty when that cell holds none.
   */
  private static Optional<StructureSite> siteIn(
      StructuresConfig config,
      StructureManager structures,
      LandLookup land,
      long worldSeed,
      int cellX,
      int cellZ
  ) {
    long hash = mix(worldSeed ^ SITE_SALT, cellX, cellZ);

    if (unitFrom(hash) >= config.getChance()) {
      return Optional.empty();
    }

    int spacing = config.getSpacing();
    double jitter = config.getJitter();

    // The site wanders inside its own cell only, so two neighbouring cells can never land on the
    // same centre chunk and fight over one placement marker.
    long jitterHash = mix(hash, 0x9E37L, 0x85EBL);
    int centerX = cellX * spacing + (int) (spacing
        * (0.5 + jitter * (unitFrom(jitterHash) - 0.5)));
    int centerZ = cellZ * spacing + (int) (spacing
        * (0.5 + jitter * (unitFrom(mix(jitterHash, 1, 1)) - 0.5)));

    StructureDefinition drawn = structures.pick(unitFrom(mix(hash, 0xC2B2L, 0x27D4L)));

    if (!drawn.suits(land, centerX, centerZ)) {
      return Optional.empty();
    }

    int rotation = (int) (unitFrom(mix(hash, 0xA5A5L, 0x5A5AL)) * 4.0);

    return Optional.of(new StructureSite(
        drawn.id(),
        centerX,
        centerZ,
        rotation,
        drawn.radius(),
        mix(hash, 0xB0B0L, 0x0B0BL)
    ));
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
