package com.edysmajler.neweracore.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldSnapshotTest {

  private static WorldSnapshot.Builder builder() {
    return new WorldSnapshot.Builder("world", 42L, -64, -64, 128, 4);
  }

  @Test
  @DisplayName("a block position reads the sample covering it")
  void samplesCoverBlocks() {
    WorldSnapshot.Builder builder = builder();
    builder.set(0, 0, 70, 0, TerrainClass.PLAINS, true, false, 0.5, 0.25);
    WorldSnapshot snapshot = builder.build();

    // One sample stands for four blocks, so every block in its square gets the same answer
    for (int offset = 0; offset < 4; offset++) {
      assertEquals(70, snapshot.heightAt(-64 + offset, -64 + offset));
    }

    assertEquals(WorldSnapshot.UNKNOWN_HEIGHT, snapshot.heightAt(-64 + 4, -64));
  }

  @Test
  @DisplayName("outside the snapshot nothing is known but everything is land")
  void outsideIsForgiving() {
    WorldSnapshot snapshot = builder().build();

    assertFalse(snapshot.contains(5000, 0));
    assertEquals(WorldSnapshot.UNKNOWN_HEIGHT, snapshot.heightAt(5000, 0));
    // Matching the plugin's own lookup: refusing to place anything is the worse failure
    assertTrue(snapshot.isLand(5000, 0));
    assertFalse(snapshot.isRugged(5000, 0));
    assertEquals(0.0, snapshot.corruptionAt(5000, 0));
  }

  @Test
  @DisplayName("everything written comes back, sites included")
  void roundTrips(@TempDir Path folder) throws IOException {
    WorldSnapshot.Builder builder = builder();
    builder.set(1, 2, 84, 6, TerrainClass.RIVER, false, false, 0.75, 0.9);
    builder.set(3, 3, 120, 0, TerrainClass.MOUNTAIN, true, true, 0.1, 0.0);
    builder.add(new PlannedSite(PlannedSite.SiteKind.STRUCTURE, "fighter_jet", 12, -40, 48, 2));
    builder.add(new PlannedSite(PlannedSite.SiteKind.TOWN, "town", -100, 30, 48, 0));

    Path file = folder.resolve("nested").resolve("world.nep");
    builder.build().write(file);
    WorldSnapshot read = WorldSnapshot.read(file);

    assertEquals("world", read.worldName());
    assertEquals(42L, read.seed());
    assertEquals(128, read.size());
    assertEquals(4, read.resolution());
    assertEquals(32, read.samplesPerSide());

    assertEquals(84, read.heightOfSample(1, 2));
    assertEquals(6, read.waterOfSample(1, 2));
    assertEquals(TerrainClass.RIVER, read.terrainOfSample(1, 2));
    assertEquals(TerrainClass.MOUNTAIN, read.terrainOfSample(3, 3));
    assertTrue(read.isRugged(read.originX() + 12, read.originZ() + 12));

    // Quantised to a byte, so exact equality is the wrong assertion; drift beyond a step is not
    assertEquals(0.75, read.corruptionOfSample(1, 2), 1.0 / 255.0);
    assertEquals(0.9, read.impactOfSample(1, 2), 1.0 / 255.0);

    assertEquals(2, read.sites().size());
    assertEquals("fighter_jet", read.sites().get(0).id());
    assertEquals(2, read.sites().get(0).rotation());
    assertEquals(PlannedSite.SiteKind.TOWN, read.sites().get(1).kind());
  }

  @Test
  @DisplayName("a file that is not a snapshot is refused rather than misread")
  void refusesRubbish(@TempDir Path folder) throws IOException {
    Path file = folder.resolve("not-a-snapshot.nep");
    Files.writeString(file, "hello");

    assertThrows(IOException.class, () -> WorldSnapshot.read(file));
  }

  @Test
  @DisplayName("a size that is not a whole number of samples is rounded up, never truncated")
  void sizeRoundsUp() {
    // Truncating would leave a strip of the planned area unsurveyed, and nothing would say so
    WorldSnapshot snapshot = new WorldSnapshot.Builder("world", 1L, 0, 0, 30, 4).build();

    assertEquals(8, snapshot.samplesPerSide());
    assertEquals(32, snapshot.size());
  }
}
