package com.edysmajler.neweracore.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldPlanFileTest {

  private static WorldPlan plan() {
    return new WorldPlan(
        123456789L,
        -1500,
        -1500,
        3000,
        List.of(
            new PlannedLocation("haven", LocationType.TOWN, "Haven", 482, -317, 180, "the first"),
            new PlannedLocation("base", LocationType.MILITARY_BASE, "Kilo", -900, 220, 240, "")
        ),
        List.of(new PlannedRoad("haven", "base"))
    );
  }

  @Test
  @DisplayName("a plan round-trips through the file unchanged")
  void roundTrips(@TempDir Path folder) throws IOException {
    Path file = folder.resolve("plans").resolve("world-plan.json");
    WorldPlanFile.write(plan(), file);

    assertEquals(plan(), WorldPlanFile.read(file));
  }

  @Test
  @DisplayName("the file is readable and editable by hand")
  void isReadable(@TempDir Path folder) throws IOException {
    Path file = folder.resolve("world-plan.json");
    WorldPlanFile.write(plan(), file);
    String json = Files.readString(file);

    // A plan is authorship, not machine data: a designer must be able to fix one in an editor
    assertTrue(json.contains("\"seed\" : 123456789"), json);
    assertTrue(json.contains("\"type\" : \"TOWN\""), json);
    assertTrue(json.contains("Haven"), json);
  }

  @Test
  @DisplayName("a plan written by a newer planner still opens")
  void toleratesUnknownFields(@TempDir Path folder) throws IOException {
    Path file = folder.resolve("newer.json");
    Files.writeString(
        file,
        """
        {
          "seed": 7,
          "originX": 0,
          "originZ": 0,
          "size": 512,
          "factions": ["not a thing yet"],
          "locations": [
            {"id": "a", "type": "BUNKER", "name": "A", "blockX": 4, "blockZ": 8, "radius": 30,
             "elevation": 91}
          ]
        }
        """
    );

    WorldPlan read = WorldPlanFile.read(file);

    assertEquals(7L, read.seed());
    assertEquals(1, read.locations().size());
    assertEquals(LocationType.BUNKER, read.locations().get(0).type());
    // A plan with no roads section is a plan with no roads, not a crash
    assertTrue(read.roads().isEmpty());
  }

  @Test
  @DisplayName("a plan knows whether it belongs to a snapshot's world")
  void detectsTheWrongSeed() {
    WorldSnapshot right = new WorldSnapshot.Builder("w", 123456789L, 0, 0, 64, 4).build();
    WorldSnapshot wrong = new WorldSnapshot.Builder("w", 987654321L, 0, 0, 64, 4).build();

    assertTrue(plan().matches(right));
    assertFalse(plan().matches(wrong));
  }

  @Test
  @DisplayName("an empty plan covers exactly the surveyed area")
  void emptyPlanTakesTheSnapshotsArea() {
    WorldSnapshot snapshot = new WorldSnapshot.Builder("w", 5L, -256, -256, 512, 4).build();
    WorldPlan empty = WorldPlan.emptyFor(snapshot);

    assertEquals(-256, empty.originX());
    assertEquals(512, empty.size());
    assertTrue(empty.locations().isEmpty());
  }
}
