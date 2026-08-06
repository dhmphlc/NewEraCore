package com.edysmajler.neweracore.world.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.HugeCraterConfig;
import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.history.HistoryEngine;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Asserts where the huge craters are allowed to be.
 *
 * <p>Testable without a server because the land test is behind {@link LandLookup} rather than being
 * a direct biome call. That seam is the only reason this file can exist: naming {@code
 * org.bukkit.block.Biome} would load the server registry and fail before the first assertion.
 */
class CraterSitesTest {

  private static final long SEED = 20260806L;
  private static final int RADIUS = 3000;

  private final WorldEngineConfig config = new WorldEngineConfig();
  private final HugeCraterConfig craters = config.getHugeCraters();
  private final HistoryEngine history = new HistoryEngine(SEED, config);

  @Test
  void findsCratersOnLand() {
    List<CraterSite> found = around(LandLookup.EVERYWHERE);

    assertFalse(found.isEmpty(), "the test seed has no craters within " + RADIUS + " blocks");
  }

  @Test
  void refusesEveryCraterAtSea() {
    // The field report that caused this: three of the first four craters anybody walked to were at
    // sea. Fluid columns are never carved, so such a site spends the rarest feature in the world on
    // a kilometre's walk to look at open water.
    assertTrue(around((x, z) -> false).isEmpty(), "a site was kept out at sea");
  }

  @Test
  void refusesCratersWhoseRimReachesWater() {
    CraterSite site = around(LandLookup.EVERYWHERE).get(0);

    // Dry everywhere except one patch of shoreline out past the rim. The centre is still perfectly
    // good land, which is exactly why testing only the centre let these through: nothing flows
    // while the chunk is written, and then the first block update near that bank fills the bowl to
    // sea level.
    int shoreX = site.centerX() + (int) Math.round(site.radius() * 1.5);
    LandLookup shoreline = (x, z) ->
        Math.hypot(x - (double) shoreX, z - (double) site.centerZ()) > 6.0;

    assertTrue(shoreline.isLand(site.centerX(), site.centerZ()), "the centre should be dry land");
    assertFalse(around(shoreline).contains(site), "a site with water at its rim was kept");
  }

  @Test
  void ordersCratersByDistance() {
    List<CraterSite> found = around(LandLookup.EVERYWHERE);

    double previous = -1.0;
    for (CraterSite site : found) {
      double distance = site.distanceTo(0, 0);
      assertTrue(distance >= previous, "sites are not ordered by distance");
      assertTrue(distance <= RADIUS, "a site outside the search radius was returned");
      previous = distance;
    }
  }

  @Test
  void sitesAreTheSameForTheSameSeed() {
    assertEquals(around(LandLookup.EVERYWHERE), around(LandLookup.EVERYWHERE));

    List<CraterSite> other = CraterSites.around(
        craters,
        new HistoryEngine(SEED + 1, config),
        LandLookup.EVERYWHERE,
        SEED + 1,
        0,
        0,
        RADIUS
    );
    assertFalse(around(LandLookup.EVERYWHERE).equals(other), "two seeds gave the same craters");
  }

  private List<CraterSite> around(LandLookup land) {
    return CraterSites.around(craters, history, land, SEED, 0, 0, RADIUS);
  }
}
