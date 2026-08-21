package com.edysmajler.neweracore.plan;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Reads the shape of the land out of a {@link WorldSnapshot}.
 *
 * <p>The counterpart to {@code TerrainProbe}, which answers the same questions inside a loaded
 * chunk. Neither can serve the other's caller: the probe reads a live chunk snapshot and stops at
 * chunk borders on purpose, while this reads a saved raster and must happily look hundreds of
 * blocks across it. What they share is the reason they exist — a decision made without reference
 * to the landform reads as painted on, whether it is a pass placing ash or a designer placing a
 * town.
 *
 * <p>Pure and free of Bukkit, so the planner runs it directly and tests can check it.
 */
public final class SnapshotTerrain {

  /** How many samples out to look when comparing a position with the land around it. */
  private static final int RELIEF_SAMPLES = 3;

  /** How many samples out to look when judging how enclosed a position is. */
  private static final int ENCLOSURE_SAMPLES = 8;

  /** How far above a position the surrounding land must rise to count as enclosing it. */
  private static final int ENCLOSURE_RISE = 4;

  /** How many samples out to search for water before reporting none. */
  private static final int WATER_SAMPLES = 24;

  /** The eight compass directions enclosure is measured along. */
  private static final int[][] DIRECTIONS = {
      {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
  };

  private final WorldSnapshot snapshot;

  /**
   * Creates a reader over one snapshot.
   *
   * @param snapshot the exported raster
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "A snapshot is read-only once built and is meant to be shared: copying half "
          + "a million samples per reader would defeat the point of loading it once."
  )
  public SnapshotTerrain(WorldSnapshot snapshot) {
    this.snapshot = snapshot;
  }

  /**
   * Takes a full reading at a position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the reading; heights read {@link WorldSnapshot#UNKNOWN_HEIGHT} outside the snapshot
   */
  public TerrainReading readingAt(int blockX, int blockZ) {
    int sampleX = sampleXof(blockX);
    int sampleZ = sampleZof(blockZ);
    int height = snapshot.heightOfSample(sampleX, sampleZ);

    return new TerrainReading(
        blockX,
        blockZ,
        height,
        snapshot.waterOfSample(sampleX, sampleZ),
        snapshot.terrainOfSample(sampleX, sampleZ),
        snapshot.isLand(blockX, blockZ),
        snapshot.isRugged(blockX, blockZ),
        slopeAt(sampleX, sampleZ, height),
        reliefAt(sampleX, sampleZ, height),
        enclosureAt(sampleX, sampleZ, height),
        waterDistanceAt(sampleX, sampleZ)
    );
  }

  private int slopeAt(int sampleX, int sampleZ, int height) {
    if (height == WorldSnapshot.UNKNOWN_HEIGHT) {
      return 0;
    }

    int worst = 0;
    for (int[] direction : DIRECTIONS) {
      int neighbour = snapshot.heightOfSample(sampleX + direction[0], sampleZ + direction[1]);
      if (neighbour != WorldSnapshot.UNKNOWN_HEIGHT) {
        worst = Math.max(worst, Math.abs(height - neighbour));
      }
    }

    return worst;
  }

  private int reliefAt(int sampleX, int sampleZ, int height) {
    if (height == WorldSnapshot.UNKNOWN_HEIGHT) {
      return 0;
    }

    int total = 0;
    int counted = 0;

    for (int[] direction : DIRECTIONS) {
      int neighbour = snapshot.heightOfSample(
          sampleX + direction[0] * RELIEF_SAMPLES,
          sampleZ + direction[1] * RELIEF_SAMPLES
      );
      if (neighbour != WorldSnapshot.UNKNOWN_HEIGHT) {
        total += neighbour;
        counted++;
      }
    }

    return counted == 0 ? 0 : height - total / counted;
  }

  private double enclosureAt(int sampleX, int sampleZ, int height) {
    if (height == WorldSnapshot.UNKNOWN_HEIGHT) {
      return 0.0;
    }

    int rising = 0;
    int counted = 0;

    for (int[] direction : DIRECTIONS) {
      // Walk out along the ray rather than only checking its end: a valley whose walls rise close
      // in is more enclosed than one whose distant hills happen to be tall, and only the walk can
      // tell the two apart.
      boolean rises = false;

      for (int step = 1; step <= ENCLOSURE_SAMPLES; step++) {
        int neighbour = snapshot.heightOfSample(
            sampleX + direction[0] * step,
            sampleZ + direction[1] * step
        );
        if (neighbour == WorldSnapshot.UNKNOWN_HEIGHT) {
          break;
        }
        if (neighbour >= height + ENCLOSURE_RISE) {
          rises = true;
          break;
        }
      }

      counted++;
      if (rises) {
        rising++;
      }
    }

    return counted == 0 ? 0.0 : rising / (double) counted;
  }

  private int waterDistanceAt(int sampleX, int sampleZ) {
    if (snapshot.waterOfSample(sampleX, sampleZ) > 0
        || snapshot.terrainOfSample(sampleX, sampleZ).isWater()) {
      return 0;
    }

    for (int ring = 1; ring <= WATER_SAMPLES; ring++) {
      for (int offset = -ring; offset <= ring; offset++) {
        if (isWaterSample(sampleX + offset, sampleZ - ring)
            || isWaterSample(sampleX + offset, sampleZ + ring)
            || isWaterSample(sampleX - ring, sampleZ + offset)
            || isWaterSample(sampleX + ring, sampleZ + offset)) {
          return ring * snapshot.resolution();
        }
      }
    }

    return -1;
  }

  private boolean isWaterSample(int sampleX, int sampleZ) {
    return snapshot.waterOfSample(sampleX, sampleZ) > 0
        || snapshot.terrainOfSample(sampleX, sampleZ).isWater();
  }

  private int sampleXof(int blockX) {
    return Math.floorDiv(blockX - snapshot.originX(), snapshot.resolution());
  }

  private int sampleZof(int blockZ) {
    return Math.floorDiv(blockZ - snapshot.originZ(), snapshot.resolution());
  }
}
