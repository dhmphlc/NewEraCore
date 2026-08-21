package com.edysmajler.neweracore.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnapshotTerrainTest {

  private static final int RESOLUTION = 4;
  private static final int SIZE = 400;

  /** Flat ground at one height, with a lake left out of it. */
  private static WorldSnapshot flat(int height) {
    WorldSnapshot.Builder builder = new WorldSnapshot.Builder("world", 1L, 0, 0, SIZE, RESOLUTION);

    for (int x = 0; x < builder.samplesPerSide(); x++) {
      for (int z = 0; z < builder.samplesPerSide(); z++) {
        builder.set(x, z, height, 0, TerrainClass.PLAINS, true, false, 0.0, 0.0);
      }
    }

    return builder.build();
  }

  /** A bowl: low in the middle, rising towards every edge. */
  private static WorldSnapshot bowl() {
    WorldSnapshot.Builder builder = new WorldSnapshot.Builder("world", 1L, 0, 0, SIZE, RESOLUTION);
    int edge = builder.samplesPerSide();
    int middle = edge / 2;

    for (int x = 0; x < edge; x++) {
      for (int z = 0; z < edge; z++) {
        int distance = Math.max(Math.abs(x - middle), Math.abs(z - middle));
        builder.set(x, z, 64 + distance, 0, TerrainClass.PLAINS, true, false, 0.0, 0.0);
      }
    }

    return builder.build();
  }

  @Test
  @DisplayName("flat ground has no slope, no relief and no enclosure")
  void flatGroundIsFlat() {
    TerrainReading reading = new SnapshotTerrain(flat(70)).readingAt(200, 200);

    assertEquals(0, reading.slope());
    assertEquals(0, reading.relief());
    assertEquals(0.0, reading.enclosure());
    assertEquals(0.0, reading.valley());
    assertTrue(reading.isBuildable());
  }

  @Test
  @DisplayName("the floor of a bowl reads as an enclosed valley")
  void bowlFloorIsValley() {
    TerrainReading reading = new SnapshotTerrain(bowl()).readingAt(200, 200);

    // Every direction rises, and it sits below the land around it: that is a valley, and the
    // single number is what a dam or a sheltered town is actually looking for
    assertEquals(1.0, reading.enclosure());
    assertTrue(reading.relief() < 0, "expected to sit below its surroundings, got "
        + reading.relief());
    assertTrue(reading.valley() > 0.5, "expected a strong valley reading, got " + reading.valley());
  }

  @Test
  @DisplayName("the rim of a bowl is not a valley, however steep it is")
  void rimIsNotValley() {
    TerrainReading reading = new SnapshotTerrain(bowl()).readingAt(8, 8);

    assertTrue(reading.relief() > 0, "expected to stand above its surroundings");
    assertEquals(0.0, reading.valley());
  }

  @Test
  @DisplayName("distance to water is measured in blocks, and reported as far when there is none")
  void waterDistance() {
    WorldSnapshot.Builder builder = new WorldSnapshot.Builder("world", 1L, 0, 0, SIZE, RESOLUTION);
    int edge = builder.samplesPerSide();

    for (int x = 0; x < edge; x++) {
      for (int z = 0; z < edge; z++) {
        boolean lake = x >= 10 && x <= 12;
        builder.set(x, z, 64, lake ? 5 : 0, lake ? TerrainClass.RIVER : TerrainClass.PLAINS,
            !lake, false, 0.0, 0.0);
      }
    }

    SnapshotTerrain terrain = new SnapshotTerrain(builder.build());

    assertEquals(0, terrain.readingAt(11 * RESOLUTION, 40).waterDistance());
    // Three samples out from the lake's edge at sample 12, and a sample is four blocks
    assertEquals(3 * RESOLUTION, terrain.readingAt(15 * RESOLUTION, 40).waterDistance());
    assertEquals(-1, new SnapshotTerrain(flat(70)).readingAt(200, 200).waterDistance());
  }

  @Test
  @DisplayName("unsurveyed ground reads as unknown rather than as flat ground at height zero")
  void outsideIsUnknown() {
    TerrainReading reading = new SnapshotTerrain(flat(70)).readingAt(99999, 99999);

    assertEquals(WorldSnapshot.UNKNOWN_HEIGHT, reading.height());
    assertEquals(0, reading.slope());
    assertEquals(0.0, reading.enclosure());
  }
}
