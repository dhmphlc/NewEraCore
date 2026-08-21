package com.edysmajler.neweracore.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TerrainClassTest {

  @Test
  @DisplayName("water beats every other word in a biome name")
  void waterWins() {
    // A frozen river is a river first: the snow is a texture, the water is a constraint
    assertEquals(TerrainClass.RIVER, TerrainClass.fromBiomeKey("frozen_river"));
    assertEquals(TerrainClass.OCEAN, TerrainClass.fromBiomeKey("deep_frozen_ocean"));
  }

  @Test
  @DisplayName("broken ground beats the vegetation on it")
  void ruggedWins() {
    assertEquals(TerrainClass.HILLS, TerrainClass.fromBiomeKey("windswept_forest"));
    assertEquals(TerrainClass.MOUNTAIN, TerrainClass.fromBiomeKey("jagged_peaks"));
  }

  @Test
  @DisplayName("the common biomes land where a designer would expect")
  void commonBiomes() {
    assertEquals(TerrainClass.PLAINS, TerrainClass.fromBiomeKey("plains"));
    assertEquals(TerrainClass.FOREST, TerrainClass.fromBiomeKey("birch_forest"));
    assertEquals(TerrainClass.TAIGA, TerrainClass.fromBiomeKey("snowy_taiga"));
    assertEquals(TerrainClass.JUNGLE, TerrainClass.fromBiomeKey("sparse_jungle"));
    assertEquals(TerrainClass.DESERT, TerrainClass.fromBiomeKey("eroded_badlands"));
    assertEquals(TerrainClass.SWAMP, TerrainClass.fromBiomeKey("mangrove_swamp"));
    assertEquals(TerrainClass.SNOW, TerrainClass.fromBiomeKey("ice_spikes"));
  }

  @Test
  @DisplayName("an unknown or missing biome is grouped, never thrown")
  void unknownIsGrouped() {
    assertEquals(TerrainClass.OTHER, TerrainClass.fromBiomeKey("some_future_biome"));
    assertEquals(TerrainClass.OTHER, TerrainClass.fromBiomeKey(null));
    assertEquals(TerrainClass.OTHER, TerrainClass.byOrdinal(9999));
  }

  @Test
  @DisplayName("only ocean and river count as water")
  void waterClasses() {
    assertTrue(TerrainClass.OCEAN.isWater());
    assertTrue(TerrainClass.RIVER.isWater());
    assertFalse(TerrainClass.BEACH.isWater());
    assertFalse(TerrainClass.SWAMP.isWater());
  }
}
