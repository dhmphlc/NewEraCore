package com.edysmajler.neweracore.world.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.InfrastructureConfig;
import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.history.Landmark;
import com.edysmajler.neweracore.world.history.LandmarkType;
import org.junit.jupiter.api.Test;

/**
 * Asserts that a runway is a runway.
 *
 * <p>Which comes down to one thing: it is at exactly one height, everywhere, whatever the ground
 * was doing. Roads are allowed to ride over hills and should — a real road follows its terrain. An
 * aircraft cannot land on a gradient, so here the ground gives way to the structure instead.
 */
class AirfieldTest {

  private static final long SEED = 20260806L;
  private static final int SEA_LEVEL = 63;

  private final InfrastructureConfig config = new WorldEngineConfig().getInfrastructure();
  private final Landmark airport = new Landmark(LandmarkType.AIRPORT, 1200, -800);

  @Test
  void onlyAirportsHaveRunways() {
    assertNotNull(Airfield.at(airport, config, SEA_LEVEL, SEED));
    assertNull(Airfield.at(
        new Landmark(LandmarkType.HOSPITAL, 1200, -800), config, SEA_LEVEL, SEED));
  }

  @Test
  void theWholeStripSitsAtOneHeight() {
    Airfield field = Airfield.at(airport, config, SEA_LEVEL, SEED);
    int height = field.platformY();

    // The reason a site can have an engineered grade when a route cannot: the height is a pure
    // function of the seed, so every chunk that touches the runway already agrees on it and only
    // has to bring its own columns to it. There is no per-position height to disagree about at all.
    // One block in from each end: rounding a position back to whole blocks can push the very last
    // row a fraction past the threshold, which is a ragged end rather than a hole.
    int reach = field.length() / 2 - 1;
    for (int step = -reach; step <= reach; step += 7) {
      int blockX = (int) Math.round(airport.centerX() + field.cos() * step);
      int blockZ = (int) Math.round(airport.centerZ() + field.sin() * step);

      assertTrue(field.covers(blockX, blockZ),
          "the strip has a hole in it " + step + " blocks along");
      assertEquals(height, field.platformY(), "the runway is not level");
    }
  }

  @Test
  void theStripIsAsLongAndWideAsAsked() {
    Airfield field = Airfield.at(airport, config, SEA_LEVEL, SEED);

    int justPast = field.length() / 2 + 3;
    assertFalse(
        field.covers(
            (int) Math.round(airport.centerX() + field.cos() * justPast),
            (int) Math.round(airport.centerZ() + field.sin() * justPast)
        ),
        "the runway runs on past its own end"
    );

    // Across the strip: paved in the middle, shoulder at the verge, nothing beyond
    int wide = field.width() / 2 + 12;
    assertFalse(field.covers(
        (int) Math.round(airport.centerX() - field.sin() * wide),
        (int) Math.round(airport.centerZ() + field.cos() * wide)));
    assertTrue(field.isPaved(airport.centerX(), airport.centerZ()));
  }

  @Test
  void theShoulderIsLevelledButNotPaved() {
    Airfield field = Airfield.at(airport, config, SEA_LEVEL, SEED);

    int verge = field.width() / 2 + 2;
    int blockX = (int) Math.round(airport.centerX() - field.sin() * verge);
    int blockZ = (int) Math.round(airport.centerZ() + field.cos() * verge);

    assertTrue(field.covers(blockX, blockZ), "the verge is not levelled with the strip");
    assertFalse(field.isPaved(blockX, blockZ), "the verge is paved like the runway");
  }

  @Test
  void theCentreLineIsDashed() {
    Airfield field = Airfield.at(airport, config, SEA_LEVEL, SEED);

    int painted = 0;
    for (int step = -40; step <= 40; step++) {
      int blockX = (int) Math.round(airport.centerX() + field.cos() * step);
      int blockZ = (int) Math.round(airport.centerZ() + field.sin() * step);

      if (field.isMarking(blockX, blockZ)) {
        painted++;
      }
    }

    // Dashes, not a solid stripe and not nothing
    assertTrue(painted > 10, "the centre line is barely painted: " + painted);
    assertTrue(painted < 70, "the centre line is a solid stripe: " + painted);
  }

  @Test
  void everyAirfieldIsTheSameForTheSameSeed() {
    Airfield once = Airfield.at(airport, config, SEA_LEVEL, SEED);
    Airfield again = Airfield.at(airport, config, SEA_LEVEL, SEED);
    Airfield elsewhere = Airfield.at(airport, config, SEA_LEVEL, SEED + 1);

    assertEquals(once, again);
    assertTrue(
        once.platformY() != elsewhere.platformY() || once.cos() != elsewhere.cos(),
        "two worlds built the same airport the same way"
    );
  }

  @Test
  void theRunwaySitsNearSeaLevelRatherThanWhereverTheTerrainWas() {
    for (int i = 0; i < 40; i++) {
      Airfield field = Airfield.at(
          new Landmark(LandmarkType.AIRPORT, i * 1301, -i * 907), config, SEA_LEVEL, SEED);

      // Sea level is the one global height a world gives away without loading anything, so it is
      // what anything needing to be level has to be anchored to.
      assertTrue(field.platformY() >= SEA_LEVEL, "a runway below sea level: " + field.platformY());
      assertTrue(
          field.platformY() <= SEA_LEVEL + config.getRunwayLift(),
          "a runway floating at " + field.platformY()
      );
    }
  }

  @Test
  void theRunwayFitsInsideItsAirport() {
    Airfield field = Airfield.at(airport, config, SEA_LEVEL, SEED);

    assertTrue(
        field.reach() <= LandmarkType.AIRPORT.footprint(),
        "the runway is longer than the airport it belongs to: " + field.reach()
            + " against a footprint of " + LandmarkType.AIRPORT.footprint()
    );
  }
}
