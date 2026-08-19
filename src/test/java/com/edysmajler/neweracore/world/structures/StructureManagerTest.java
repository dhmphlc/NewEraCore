package com.edysmajler.neweracore.world.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StructureManagerTest {

  @Test
  void refusesDuplicateIds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StructureManager(List.of(new FighterJet(), new FighterJet()))
    );
  }

  @Test
  void refusesWeightlessStructures() {
    StructureDefinition weightless = definition("hollow", 8, 0.0);

    assertThrows(
        IllegalArgumentException.class,
        () -> new StructureManager(List.of(weightless))
    );
  }

  @Test
  void theWindowIsDerivedFromTheBiggestStructure() {
    StructureManager manager = new StructureManager(List.of(
        definition("shed", 6, 1.0),
        definition("works", 90, 1.0),
        definition("hut", 4, 1.0)
    ));

    // The candidate window is derived from this; understating it silently loses placements
    assertEquals(90, manager.maxRadius());
  }

  @Test
  void everyWeightedRollLandsOnSomethingRegistered() {
    StructureManager manager = new StructureManager(List.of(
        definition("common", 8, 3.0),
        definition("rare", 8, 1.0)
    ));

    int rare = 0;
    for (int i = 0; i < 1000; i++) {
      StructureDefinition drawn = manager.pick(i / 1000.0);
      assertTrue(manager.byId(drawn.id()).isPresent());
      if (drawn.id().equals("rare")) {
        rare++;
      }
    }

    // A 1-in-4 weight should draw roughly a quarter of a uniform sweep
    assertTrue(rare > 150 && rare < 350, "rare drew " + rare + " of 1000");
  }

  private static StructureDefinition definition(String id, int radius, double weight) {
    return new StructureDefinition() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public int radius() {
        return radius;
      }

      @Override
      public double weight() {
        return weight;
      }

      @Override
      public void place(StructureField field, StructureSite site) {
        // Siting-only test double; nothing is ever built
      }
    };
  }
}
