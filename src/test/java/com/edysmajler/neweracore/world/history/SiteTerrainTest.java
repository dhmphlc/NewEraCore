package com.edysmajler.neweracore.world.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.WorldEngineConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Asserts that a place could have been built where it stands.
 *
 * <p>The story says whether a landmark belongs in this region; this says whether the ground does.
 * Leaving the second question out produced exactly the artefact it sounds like — a hydroelectric
 * dam on dry flat land, holding nothing back — and a place that could not have been built where it
 * stands is worse than no place at all, because it tells the player nothing was thought about.
 */
class SiteTerrainTest {

  private static final long SEED = 20260806L;
  private static final int CELLS = 14;

  private final WorldEngineConfig config = new WorldEngineConfig();

  @Test
  void nothingThatNeedsWaterIsBuiltOnDryLand() {
    List<Landmark> sites = sitesWith(new SiteTerrain() {
      @Override
      public boolean isWaterside(int blockX, int blockZ) {
        return false;
      }
    });

    assertTrue(!sites.isEmpty(), "no sites to check");

    for (Landmark site : sites) {
      assertTrue(
          site.type() != LandmarkType.HYDROELECTRIC_DAM,
          site.type() + " was built with no water in reach at "
              + site.centerX() + ", " + site.centerZ()
      );
    }
  }

  @Test
  void noAirportIsBuiltInTheHills() {
    List<Landmark> sites = sitesWith(new SiteTerrain() {
      @Override
      public boolean isOpen(int blockX, int blockZ) {
        return false;
      }
    });

    for (Landmark site : sites) {
      // A runway can be levelled wherever it lands. The ridge an aircraft would have to fly through
      // on approach cannot be, so the answer is not to put the airport there.
      assertEquals(
          false,
          site.type() == LandmarkType.AIRPORT,
          "an airport in the hills at " + site.centerX() + ", " + site.centerZ()
      );
    }
  }

  @Test
  void nothingIsBuiltAtSea() {
    // The hole that let a hospital and a radio mast be sited in an ocean: only the places that
    // wanted water were ever asked about the ground, so everything else was placed wherever the
    // grid said. Ground that suits nothing has to hold nothing.
    assertTrue(
        sitesWith(new SiteTerrain() {
          @Override
          public boolean isDryLand(int blockX, int blockZ) {
            return false;
          }
        }).isEmpty(),
        "landmarks were placed on open water"
    );
  }

  @Test
  void groundThatIsMerelyAwkwardStillHoldsSomething() {
    // Dry land with no water and no open country suits neither a dam nor an airport, but a radio
    // tower has no story affinity and no ground requirement — so the cell changes what it holds
    // rather than losing it.
    int awkward = sitesWith(new SiteTerrain() {
      @Override
      public boolean isWaterside(int blockX, int blockZ) {
        return false;
      }

      @Override
      public boolean isOpen(int blockX, int blockZ) {
        return false;
      }
    }).size();

    assertEquals(sitesWith(SiteTerrain.ANYWHERE).size(), awkward,
        "constraining the ground lost whole sites instead of changing their kind");
  }

  @Test
  void waterGroundStillProducesDams() {
    List<Landmark> sites = sitesWith(SiteTerrain.ANYWHERE);

    assertTrue(
        sites.stream().anyMatch(site -> site.type() == LandmarkType.HYDROELECTRIC_DAM),
        "no dam anywhere, so the constraint has removed the type rather than placed it"
    );
  }

  private List<Landmark> sitesWith(SiteTerrain terrain) {
    HistoryEngine engine = new HistoryEngine(SEED, config, terrain);
    List<Landmark> sites = new ArrayList<>();

    for (int x = -CELLS / 2; x < CELLS / 2; x++) {
      for (int z = -CELLS / 2; z < CELLS / 2; z++) {
        engine.landmarks().siteIn(x, z).ifPresent(sites::add);
      }
    }

    return sites;
  }
}
