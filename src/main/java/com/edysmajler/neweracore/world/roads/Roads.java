package com.edysmajler.neweracore.world.roads;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ChunkProcessor;
import com.edysmajler.neweracore.world.corruption.CorruptionLevel;
import java.util.List;
import org.bukkit.Material;

/**
 * Paves the road network, one chunk-slice at a time.
 *
 * <p>Roads are the deliberate opposite of structures: a continuous surface mark, not a silhouette,
 * so slice-per-chunk rendering is safe — every chunk computes the same centreline from the seed
 * and paves its own sixteen columns of it. The road hugs each column's own ground, exactly like
 * the crash trench: asphalt graded through hills would need terrain the pass is not allowed to
 * read, and a road climbing with the land reads as a road anyway.
 *
 * <p>This pass runs <em>first</em>, before the ashfall, because roads are infrastructure from
 * before the event. Each paved column is reserved, which is the contract {@code ChunkContext}
 * offers for exactly this: the mantle lays its dust over the asphalt but never repaves it, craters
 * and crashes still tear it up — the event outranks the infrastructure — and the world reads in
 * the right order.
 *
 * <p>Weathering is what keeps twenty-year-old roads honest. The paving is a worn mix rather than
 * clean asphalt, edges crumble by the detail field, and whole stretches vanish under the drifts —
 * more of them the worse the corruption — so a road fades out and re-emerges instead of striping
 * the wasteland with fresh tarmac. Fluid columns are never paved: a road that dips into a river
 * and climbs out the far bank is a washed-out crossing, which is the truth here anyway.
 */
public final class Roads implements ChunkProcessor {

  /** How far past the ragged edge the gravel shoulder can reach. */
  private static final double SHOULDER = 1.5;

  /** Arc step between utility pole candidates along a highway. */
  private static final double POLE_STEP = 42.0;

  @Override
  public String name() {
    return "roads";
  }

  @Override
  public void process(ChunkContext context) {
    List<RoadSegment> segments = context.roads().segments();
    if (segments.isEmpty()) {
      return;
    }

    for (int x = 0; x < ChunkContext.CHUNK_SIZE; x++) {
      for (int z = 0; z < ChunkContext.CHUNK_SIZE; z++) {
        paveColumn(context, segments, x, z);
      }
    }

    for (RoadSegment segment : segments) {
      if (segment.kind() == RoadKind.HIGHWAY) {
        poles(context, segment);
      }
    }
  }

  private void paveColumn(ChunkContext context, List<RoadSegment> segments, int x, int z) {
    int worldX = context.blockX(x);
    int worldZ = context.blockZ(z);

    RoadSegment best = null;
    RoadSegment.Nearest nearest = null;

    for (RoadSegment segment : segments) {
      RoadSegment.Nearest candidate = segment.nearest(worldX, worldZ);
      if (nearest == null || candidate.distance() < nearest.distance()) {
        best = segment;
        nearest = candidate;
      }
    }

    if (best == null
        || nearest.distance() > best.kind().halfWidth() + 0.8 + SHOULDER
        || context.isFluidColumn(x, z)) {
      return;
    }

    // Whole stretches lie under the drifts, more of them the deeper the corruption: a road that
    // fades out and re-emerges is twenty years old, an unbroken stripe is fresh tarmac
    if (context.patchAt(x, z) > buriedThreshold(context.level())) {
      return;
    }

    long columnHash = mix(best.seed(), worldX, worldZ);

    // Edges crumble by the detail field rather than running ruler-straight
    double edge = best.kind().halfWidth() + (context.detailAt(x, z) - 0.5) * 1.6;

    if (nearest.distance() > edge) {
      if (nearest.distance() <= edge + SHOULDER && unitFrom(columnHash) < 0.4) {
        int ground = context.groundY(x, z);
        context.set(x, ground, z,
            unitFrom(mix(columnHash, 1, 1)) < 0.6 ? Material.GRAVEL : Material.COARSE_DIRT);
        context.reserve(x, z);
      }
      return;
    }

    int ground = context.groundY(x, z);
    context.set(x, ground, z, paving(best.kind(), nearest, columnHash));
    context.reserve(x, z);
  }

  /**
   * Returns one block of worn paving.
   *
   * <p>Highways carry the dashed centre line where the surface survives well enough to show it;
   * local roads are a rougher mix that shades toward gravel country lanes.
   */
  private static Material paving(RoadKind kind, RoadSegment.Nearest nearest, long columnHash) {
    double roll = unitFrom(columnHash);

    if (kind == RoadKind.HIGHWAY) {
      // The median dash: the single strongest "this was a highway" signal a ruin can keep
      if (nearest.distance() < 0.7 && nearest.arc() % 9.0 < 4.0 && roll < 0.8) {
        return Material.YELLOW_TERRACOTTA;
      }

      if (roll < 0.42) {
        return Material.GRAY_CONCRETE;
      }
      if (roll < 0.60) {
        return Material.ANDESITE;
      }
      if (roll < 0.72) {
        return Material.LIGHT_GRAY_CONCRETE;
      }
      if (roll < 0.82) {
        return Material.TUFF;
      }
      return roll < 0.93 ? Material.GRAVEL : Material.COARSE_DIRT;
    }

    if (roll < 0.28) {
      return Material.GRAVEL;
    }
    if (roll < 0.48) {
      return Material.ANDESITE;
    }
    if (roll < 0.65) {
      return Material.GRAY_CONCRETE;
    }
    if (roll < 0.80) {
      return Material.DIRT_PATH;
    }
    return roll < 0.92 ? Material.COARSE_DIRT : Material.TUFF;
  }

  /**
   * Strings the utility poles along a highway's shoulder.
   *
   * <p>Polished basalt rather than logs: a pole placed after the tree passes would stand as fresh
   * timber in a charred world, and basalt is what the dead trees themselves are rebuilt from.
   * Poles are single columns, so slice-per-chunk placement cannot seam them; a crossarm block that
   * falls in the neighbouring chunk is simply dropped, and a one-armed pole is a ruin detail, not
   * a bug.
   */
  private void poles(ChunkContext context, RoadSegment segment) {
    int steps = (int) (segment.length() / POLE_STEP);
    // One side of the road for the whole run, like a real wire run
    double side = unitFrom(mix(segment.seed(), 5, 5)) < 0.5 ? 1.0 : -1.0;

    for (int i = 1; i < steps; i++) {
      long hash = mix(segment.seed(), 0x907EL, i);

      // The missing poles came down years ago
      if (unitFrom(hash) < 0.3) {
        continue;
      }

      double[] point = segment.pointAt(i * POLE_STEP);
      double[] direction = segment.directionAt(i * POLE_STEP);
      double offset = segment.kind().halfWidth() + 2.5;

      int poleX = (int) Math.round(point[0] - direction[1] * offset * side);
      int poleZ = (int) Math.round(point[1] + direction[0] * offset * side);

      int x = poleX - context.blockX(0);
      int z = poleZ - context.blockZ(0);

      if (!context.inChunk(x, z) || context.isFluidColumn(x, z)) {
        continue;
      }

      int ground = context.groundY(x, z);
      int height = 5 + (int) (unitFrom(mix(hash, 3, 3)) * 2.0);

      for (int y = 1; y <= height; y++) {
        context.set(x, ground + y, z, Material.POLISHED_BASALT);
      }
      context.reserve(x, z);

      // The crossarm, square across the road
      int armX = (int) Math.round(-direction[1]);
      int armZ = (int) Math.round(direction[0]);
      context.set(x + armX, ground + height, z + armZ, Material.NETHER_BRICK_FENCE);
      context.set(x - armX, ground + height, z - armZ, Material.NETHER_BRICK_FENCE);
    }
  }

  private static double buriedThreshold(CorruptionLevel level) {
    return switch (level) {
      case RECOVERED -> 0.88;
      case SCARRED -> 0.80;
      case DEVASTATED -> 0.68;
    };
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
