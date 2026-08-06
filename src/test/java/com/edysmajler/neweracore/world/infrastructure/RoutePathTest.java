package com.edysmajler.neweracore.world.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoutePathTest {

  private static final long SEED = 20260806L;

  @Test
  void thePathStartsAndEndsWhereItWasAsked() {
    RoutePath path = RoutePath.between(100, -200, 1400, 600, SEED);

    assertEquals(100.0, path.sampleX(0), 0.001);
    assertEquals(-200.0, path.sampleZ(0), 0.001);
    assertEquals(1400.0, path.sampleX(path.sampleCount() - 1), 0.001);
    assertEquals(600.0, path.sampleZ(path.sampleCount() - 1), 0.001);
  }

  @Test
  void consecutiveSamplesNeverLeaveGaps() {
    RoutePath path = RoutePath.between(-500, -500, 900, 1200, SEED);

    for (int i = 1; i < path.sampleCount(); i++) {
      double step = Math.hypot(
          path.sampleX(i) - path.sampleX(i - 1),
          path.sampleZ(i) - path.sampleZ(i - 1)
      );

      // Painting works by walking these samples and colouring around each one. A step wider than a
      // block would leave holes in the road, one gap per sample, the whole way along.
      assertTrue(step <= 1.0, "samples are " + step + " blocks apart, which would leave gaps");
    }
  }

  @Test
  void thePathBendsButDoesNotWander() {
    RoutePath path = RoutePath.between(0, 0, 2000, 0, SEED);

    double furthest = 0.0;
    for (int i = 0; i < path.sampleCount(); i++) {
      furthest = Math.max(furthest, Math.abs(path.sampleZ(i)));
    }

    // Straight lines between points read as a diagram; a road that wanders halfway across the map
    // to get somewhere reads as broken. The bend belongs in between.
    assertTrue(furthest > 5.0, "the path is perfectly straight");
    assertTrue(furthest < 400.0, "the path wanders " + furthest + " blocks off course");
  }

  @Test
  void thePathIsTheSameForTheSameEnds() {
    RoutePath once = RoutePath.between(10, 20, 800, 900, SEED);
    RoutePath again = RoutePath.between(10, 20, 800, 900, SEED);
    RoutePath elsewhere = RoutePath.between(10, 20, 800, 900, SEED + 1);

    assertEquals(once.sampleX(50), again.sampleX(50), 0.0);
    assertEquals(once.sampleZ(50), again.sampleZ(50), 0.0);
    assertTrue(Math.abs(once.sampleZ(50) - elsewhere.sampleZ(50)) > 0.0,
        "two worlds bend the same road the same way");
  }

  @Test
  void positionOnThePathReportsNoDistance() {
    RoutePath path = RoutePath.between(0, 0, 600, 300, SEED);
    int midX = (int) Math.round(path.sampleX(path.sampleCount() / 2));
    int midZ = (int) Math.round(path.sampleZ(path.sampleCount() / 2));

    assertTrue(path.distanceTo(midX, midZ) < 1.0);
    assertTrue(path.distanceTo(midX, midZ + 500) > 400.0);
  }
}
