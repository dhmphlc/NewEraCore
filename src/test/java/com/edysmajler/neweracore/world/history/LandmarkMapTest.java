package com.edysmajler.neweracore.world.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.HistoryConfig;
import com.edysmajler.neweracore.config.WorldEngineConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Asserts that landmarks are where the design says they are.
 *
 * <p>The distances matter more than they look. A landmark you stumble over every few hundred blocks
 * is scenery; one you have to set out for is a destination, and the difference is the entire reason
 * for having them. So the spacing is measured, not assumed.
 */
class LandmarkMapTest {

  private static final long SEED = 20260806L;
  private static final int CELLS = 24;

  private final WorldEngineConfig config = new WorldEngineConfig();
  private final HistoryConfig history = config.getHistory();
  private final HistoryEngine engine = new HistoryEngine(SEED, config);
  private final LandmarkMap landmarks = engine.landmarks();

  @Test
  void sitesAreSpacedLikeDestinationsNotScenery() {
    List<Landmark> sites = allSites();
    assertTrue(sites.size() > 300, "not enough sites sampled: " + sites.size());

    double closest = Double.MAX_VALUE;
    double total = 0.0;

    for (Landmark site : sites) {
      double nearest = Double.MAX_VALUE;

      for (Landmark other : sites) {
        if (other == site) {
          continue;
        }
        nearest = Math.min(nearest, other.distanceTo(site.centerX(), site.centerZ()));
      }

      closest = Math.min(closest, nearest);
      total += nearest;
    }

    double mean = total / sites.size();

    // Jitter is bounded to the middle of each cell precisely so that two sites either side of a
    // cell border cannot end up neighbours, which is the failure that would turn a grid into
    // clusters.
    assertTrue(closest > 700.0, "two landmarks only " + Math.round(closest) + " blocks apart");
    assertTrue(mean > 1000.0, "landmarks average " + Math.round(mean) + " blocks apart");
    assertTrue(mean < 2600.0, "landmarks average " + Math.round(mean) + " blocks apart");
  }

  @Test
  void nowhereIsFarFromSomewhere() {
    double furthest = 0.0;

    for (int x = 0; x < 40; x++) {
      for (int z = 0; z < 40; z++) {
        int blockX = x * 337 - 6000;
        int blockZ = z * 337 - 6000;

        Optional<Landmark> nearest = landmarks.nearest(blockX, blockZ);
        assertTrue(nearest.isPresent(), "no landmark near " + blockX + ", " + blockZ);
        furthest = Math.max(furthest, nearest.get().distanceTo(blockX, blockZ));
      }
    }

    // The upper end of the requested range: wherever the player stands, there is something to walk
    // to
    assertTrue(furthest < 3000.0, "furthest walk to a landmark is " + Math.round(furthest));
  }

  @Test
  void everySiteSuitsTheRegionItStandsIn() {
    for (Landmark site : allSites()) {
      RegionStory story = engine.at(site.centerX(), site.centerZ()).story();

      // A silo in a valley the war never reached explains nothing. The affinity is what makes a
      // landmark evidence of the region's history rather than decoration dropped on top of it.
      assertTrue(
          site.type().fits(story),
          site.type() + " placed in " + story + " at " + site.centerX() + ", " + site.centerZ()
      );
    }
  }

  @Test
  void landmarkIsFoundByWhatStandsOnIt() {
    Landmark site = allSites().get(0);

    assertEquals(Optional.of(site), landmarks.covering(site.centerX(), site.centerZ()));
    assertEquals(
        Optional.empty(),
        landmarks.covering(site.centerX() + site.radius() + 40, site.centerZ())
    );
  }

  @Test
  void sitesAreDeterministicPerSeed() {
    LandmarkMap same = new HistoryEngine(SEED, new WorldEngineConfig()).landmarks();
    LandmarkMap other = new HistoryEngine(SEED + 1, new WorldEngineConfig()).landmarks();

    assertEquals(landmarks.siteIn(3, -4), same.siteIn(3, -4));
    assertNotEquals(landmarks.near(0, 0), other.near(0, 0));
  }

  @Test
  void someCellsAreLeftEmptySoTheGridDoesNotShow() {
    int empty = 0;

    for (int x = 0; x < CELLS; x++) {
      for (int z = 0; z < CELLS; z++) {
        if (landmarks.siteIn(x, z).isEmpty()) {
          empty++;
        }
      }
    }

    double share = empty / (double) (CELLS * CELLS);
    double expected = 1.0 - history.getLandmarks().getChance();

    assertTrue(share > expected / 3.0, "every cell is occupied, so the lattice will show");
    assertTrue(share < 0.5, "half the cells are empty: " + Math.round(share * 100) + "%");
  }

  private List<Landmark> allSites() {
    List<Landmark> sites = new ArrayList<>();

    for (int x = -CELLS / 2; x < CELLS / 2; x++) {
      for (int z = -CELLS / 2; z < CELLS / 2; z++) {
        landmarks.siteIn(x, z).ifPresent(sites::add);
      }
    }

    return sites;
  }
}
