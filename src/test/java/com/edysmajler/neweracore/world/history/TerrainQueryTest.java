package com.edysmajler.neweracore.world.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.world.history.TerrainQuery.Ground;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Asserts that the ground around a site is described, not guessed at.
 *
 * <p>The point of these is the difference between this seam and the three booleans it succeeds. The
 * old waterside test meant <em>a river or ocean biome within 72 blocks</em>, and it said yes to a
 * flat coastal plain, to a river crossing open grassland, and to a river valley alike. Only the
 * last of those is a place you could build a dam. So the test that matters is not that water is
 * detected — it is that ground which merely has water near it is told apart from ground that could
 * hold it.
 */
class TerrainQueryTest {

  /** A ground the tests can lay out by hand, counting what the query asks it. */
  private static final class FakeGround implements TerrainQuery {

    private final List<int[]> asked = new ArrayList<>();
    private Ground everywhere = Ground.OPEN;
    private Ground centre;
    private Ground beyond;
    private int beyondRadius = Integer.MAX_VALUE;

    @Override
    public Ground groundAt(int blockX, int blockZ) {
      asked.add(new int[] {blockX, blockZ});

      if (centre != null && blockX == 0 && blockZ == 0) {
        return centre;
      }

      double distance = Math.sqrt(blockX * (double) blockX + blockZ * (double) blockZ);

      // Slack of two blocks: a ring point rounded to integer coordinates lands just inside its
      // own radius on the diagonals, so an exact comparison would miss half the ring.
      if (beyond != null && distance >= beyondRadius - 2) {
        return beyond;
      }

      return everywhere;
    }
  }

  @Test
  void siteAtSeaIsNotMeasuredAtAll() {
    FakeGround ground = new FakeGround();
    ground.centre = Ground.OCEAN;

    SiteGround site = ground.at(0, 0);

    assertFalse(site.dryLand(), "open water was treated as buildable ground");
    // The short circuit is the point: most rejected cells in an ocean world are rejected here, and
    // measuring the surroundings of a site that cannot exist is twenty-four wasted questions.
    assertEquals(1, ground.asked.size(), "the surroundings of a site at sea were sampled anyway");
  }

  @Test
  void groundIsSampledOnceForEveryQuestionAskedOfIt() {
    FakeGround ground = new FakeGround();

    SiteGround site = ground.at(0, 0);

    // Three rings of eight, plus the centre. Every question after this is arithmetic on the record,
    // which is the whole reason the record exists: dry land used to be asked once per candidate
    // type, seven identical questions about the same block.
    assertEquals(1 + 3 * TerrainQuery.SAMPLES, ground.asked.size());

    assertEquals(1.0, site.openness(), 1e-9);
    assertEquals(0.0, site.valley(), 1e-9);
    assertFalse(site.isWaterside());

    assertEquals(1 + 3 * TerrainQuery.SAMPLES, ground.asked.size(), "asking cost more questions");
  }

  @Test
  void riverOnOpenGroundHoldsNothingBack() {
    FakeGround ground = new FakeGround();
    ground.everywhere = Ground.RIVER;
    ground.centre = Ground.OPEN;

    SiteGround site = ground.at(0, 0);

    assertTrue(site.dryLand(), "the site itself was dry");
    assertTrue(site.isWaterside(), "a river all around is water in reach");
    // The case the old seam could not tell from a dam site. There is water, and plenty of it, but
    // nothing high anywhere to hold it between, so damming it would hold nothing back.
    assertEquals(0.0, site.valley(), 1e-9, "a river across flat ground was read as a valley");
  }

  @Test
  void hillsWithNoWaterInThemHoldNothingEither() {
    FakeGround ground = new FakeGround();
    ground.everywhere = Ground.RUGGED;

    SiteGround site = ground.at(0, 0);

    assertFalse(site.isWaterside(), "there was no water anywhere");
    assertEquals(0.0, site.valley(), 1e-9, "a dry mountain range was read as a valley");
    assertEquals(0.0, site.openness(), 1e-9, "broken ground was read as open country");
  }

  @Test
  void riverHeldBetweenHighGroundReadsAsValley() {
    FakeGround ground = new FakeGround();
    ground.everywhere = Ground.RIVER;
    ground.centre = Ground.OPEN;
    ground.beyond = Ground.RUGGED;
    ground.beyondRadius = TerrainQuery.FAR;

    SiteGround site = ground.at(0, 0);

    assertTrue(site.dryLand(), "the bank itself is dry ground to build on");
    assertTrue(site.isWaterside(), "there is a river in reach");
    assertEquals(1.0, site.enclosure(), 1e-9, "the far ring was entirely high ground");
    // Water to hold back and high ground to hold it between. This is the only one of the four that
    // is a dam site, and the only one that scores.
    assertTrue(site.valley() > 0.9, "a river valley scored only " + site.valley());
  }

  @Test
  void openCountryIsToldFromBrokenGround() {
    FakeGround open = new FakeGround();
    FakeGround broken = new FakeGround();
    broken.everywhere = Ground.RUGGED;

    assertEquals(1.0, open.at(0, 0).openness(), 1e-9, "open country did not read as open");
    assertTrue(
        broken.at(0, 0).openness() < open.at(0, 0).openness(),
        "hills read as open as a plain, so a runway would be laid through them"
    );
  }

  @Test
  void siteFootingIsNotConfusedWithItsSetting() {
    // Rugged close in but open beyond: the site stands on broken ground within a wide plain. Relief
    // is a question about the setting, so the near ring must not vote on it — otherwise every site
    // on a slight rise reads as unbuildable hill country.
    FakeGround ground = new FakeGround();
    ground.everywhere = Ground.RUGGED;
    ground.centre = Ground.OPEN;
    ground.beyond = Ground.OPEN;
    ground.beyondRadius = TerrainQuery.MID;

    SiteGround site = ground.at(0, 0);

    assertEquals(
        0.0, site.relief(), 1e-9, "the site's own footing was counted as its surroundings");
  }

  @Test
  void groundWithNothingToSayPlacesWhatItAlwaysPlaced() {
    SiteGround site = TerrainQuery.ANYWHERE.at(0, 0);

    // A record of zeroes would read as a dry inland plain and quietly refuse every site that wants
    // water, which is the opposite of permissive.
    assertTrue(site.dryLand());
    assertTrue(site.isWaterside());
    assertTrue(site.valley() > 0.0);
    assertEquals(1.0, site.openness(), 1e-9);
  }

  @Test
  void theSameGroundAlwaysGivesTheSameAnswer() {
    FakeGround ground = new FakeGround();
    ground.everywhere = Ground.RIVER;
    ground.beyond = Ground.RUGGED;
    ground.beyondRadius = TerrainQuery.MID;

    // Every system that asks about a site has to get the same answer without coordinating, which is
    // what makes a landmark knowable from arbitrarily far away.
    assertEquals(ground.at(400, -1200), ground.at(400, -1200));
  }
}
