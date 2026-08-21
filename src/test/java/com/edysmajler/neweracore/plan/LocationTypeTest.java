package com.edysmajler.neweracore.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.edysmajler.neweracore.plan.LocationType.Rating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocationTypeTest {

  private static TerrainReading ground(
      int slope,
      int relief,
      double enclosure,
      int waterDistance,
      int waterDepth
  ) {
    return new TerrainReading(
        0,
        0,
        70,
        waterDepth,
        waterDepth > 0 ? TerrainClass.RIVER : TerrainClass.PLAINS,
        waterDepth == 0,
        false,
        slope,
        relief,
        enclosure,
        waterDistance
    );
  }

  @Test
  @DisplayName("a dam wants a narrow valley with water in it")
  void damWantsValley() {
    assertEquals(Rating.GOOD, LocationType.DAM.rate(ground(6, -4, 0.8, 20, 0)).rating());
    assertEquals(Rating.POOR, LocationType.DAM.rate(ground(6, -4, 0.8, -1, 0)).rating());
    assertEquals(Rating.POOR, LocationType.DAM.rate(ground(1, 0, 0.0, 10, 0)).rating());
  }

  @Test
  @DisplayName("a dam is the one thing that may stand in the water")
  void damMayStandInWater() {
    // Everything else is refused outright there, so the water test cannot come first for all types
    assertNotEquals(Rating.POOR, LocationType.DAM.rate(ground(5, -3, 0.7, 0, 4)).rating());
    assertEquals(Rating.POOR, LocationType.TOWN.rate(ground(0, 0, 0.0, 0, 4)).rating());
  }

  @Test
  @DisplayName("a tower wants height, an airport wants none of it")
  void heightCutsBothWays() {
    assertEquals(Rating.GOOD, LocationType.RADIO_TOWER.rate(ground(3, 9, 0.1, 400, 0)).rating());
    assertEquals(Rating.POOR, LocationType.RADIO_TOWER.rate(ground(3, 0, 0.8, 400, 0)).rating());
    assertEquals(Rating.GOOD, LocationType.AIRPORT.rate(ground(1, 0, 0.0, 400, 0)).rating());
    assertEquals(Rating.POOR, LocationType.AIRPORT.rate(ground(9, 0, 0.0, 400, 0)).rating());
  }

  @Test
  @DisplayName("a settlement wants level ground within reach of water")
  void settlementWantsLevelGround() {
    assertEquals(Rating.GOOD, LocationType.TOWN.rate(ground(2, 0, 0.1, 120, 0)).rating());
    assertEquals(Rating.FAIR, LocationType.TOWN.rate(ground(2, 0, 0.1, -1, 0)).rating());
    assertEquals(Rating.POOR, LocationType.TOWN.rate(ground(12, 0, 0.1, 120, 0)).rating());
  }

  @Test
  @DisplayName("a bunker refuses to be dug where it would flood")
  void bunkerAvoidsWater() {
    assertEquals(Rating.POOR, LocationType.BUNKER.rate(ground(2, 3, 0.2, 8, 0)).rating());
    assertEquals(Rating.GOOD, LocationType.BUNKER.rate(ground(2, 3, 0.2, 200, 0)).rating());
  }

  @Test
  @DisplayName("unsurveyed ground is never judged")
  void unsurveyedIsNotJudged() {
    TerrainReading unknown = new TerrainReading(0, 0, WorldSnapshot.UNKNOWN_HEIGHT, 0,
        TerrainClass.OTHER, true, false, 0, 0, 0.0, -1);

    for (LocationType type : LocationType.values()) {
      // The tool must not talk a designer out of a site it knows nothing about
      assertEquals(Rating.FAIR, type.rate(unknown).rating(), type.name());
    }
  }
}
