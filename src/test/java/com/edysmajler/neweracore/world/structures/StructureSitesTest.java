package com.edysmajler.neweracore.world.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.StructuresConfig;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Asserts where structures are allowed to be, and that every chunk agrees about them.
 *
 * <p>Testable without a server because the land test is behind {@link LandLookup} rather than
 * being a direct biome call, and the siting itself is pure arithmetic over the seed.
 */
class StructureSitesTest {

  private static final long SEED = 20260819L;
  private static final int RADIUS = 6000;

  private final StructuresConfig config = new StructuresConfig();
  private final StructureManager structures = new StructureManager(List.of(new FighterJet()));

  @Test
  void findsSitesOnLand() {
    List<StructureSite> found = around(LandLookup.EVERYWHERE);

    assertFalse(found.isEmpty(), "the test seed has no sites within " + RADIUS + " blocks");
  }

  @Test
  void refusesEverySiteAtSea() {
    // A wreck in mid-ocean is worse than no wreck: it tells the player nothing was thought about
    assertTrue(around((x, z) -> false).isEmpty(), "a site was kept out at sea");
  }

  @Test
  void sitesAreTheSameForTheSameSeed() {
    assertEquals(around(LandLookup.EVERYWHERE), around(LandLookup.EVERYWHERE));

    List<StructureSite> other = StructureSites.around(
        config, structures, LandLookup.EVERYWHERE, SEED + 1, 0, 0, RADIUS);
    assertNotEquals(around(LandLookup.EVERYWHERE), other, "two seeds gave the same sites");
  }

  @Test
  void ordersSitesByDistance() {
    double previous = -1.0;

    for (StructureSite site : around(LandLookup.EVERYWHERE)) {
      double distance = site.distanceTo(0, 0);
      assertTrue(distance >= previous, "sites are not ordered by distance");
      assertTrue(distance <= RADIUS, "a site outside the search radius was returned");
      previous = distance;
    }
  }

  @Test
  void everySiteIsSomethingTheRegistryCanBuild() {
    for (StructureSite site : around(LandLookup.EVERYWHERE)) {
      assertTrue(structures.byId(site.structureId()).isPresent(),
          site.structureId() + " is not in the registry");
      assertTrue(site.rotation() >= 0 && site.rotation() <= 3,
          "rotation out of range: " + site.rotation());
      assertEquals(structures.byId(site.structureId()).orElseThrow().radius(), site.radius(),
          "a site does not carry its structure's radius");
    }
  }

  /**
   * The invariant whole-shape placement stands on: every chunk a footprint touches sees the site.
   *
   * <p>The candidate window is derived from the registry's largest radius rather than picked by
   * eye, because the failure is silent: a chunk near the footprint's edge that never considers the
   * site simply never takes part in triggering the placement, and if that chunk happens to be the
   * last one generated, the structure never appears at all.
   */
  @Test
  void everyChunkTheFootprintTouchesSeesTheSite() {
    for (StructureSite site : around(LandLookup.EVERYWHERE)) {
      int minChunkX = (site.centerX() - site.radius()) >> 4;
      int maxChunkX = (site.centerX() + site.radius()) >> 4;
      int minChunkZ = (site.centerZ() - site.radius()) >> 4;
      int maxChunkZ = (site.centerZ() + site.radius()) >> 4;

      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
          if (!site.touchesChunk(chunkX, chunkZ)) {
            continue;
          }

          List<StructureSite> seen = StructureSites.near(
              config, structures, LandLookup.EVERYWHERE, SEED, chunkX, chunkZ);

          assertTrue(seen.contains(site),
              "chunk " + chunkX + ", " + chunkZ + " misses the site its ground is part of");
        }
      }
    }
  }

  @Test
  void chunksOutsideTheFootprintClaimNothing() {
    for (StructureSite site : around(LandLookup.EVERYWHERE)) {
      // A chunk comfortably outside the footprint must not think it takes part
      int farChunkX = (site.centerX() + site.radius() + 64) >> 4;
      int farChunkZ = (site.centerZ() + site.radius() + 64) >> 4;

      List<StructureSite> seen = StructureSites.near(
          config, structures, LandLookup.EVERYWHERE, SEED, farChunkX, farChunkZ);

      assertFalse(seen.contains(site), "a chunk outside the footprint claims the site");
    }
  }

  @Test
  void disabledScatterPlacesNothing() {
    StructuresConfig disabled = new StructuresConfig() {
      @Override
      public boolean isEnabled() {
        return false;
      }
    };

    assertTrue(
        StructureSites.near(disabled, structures, LandLookup.EVERYWHERE, SEED, 0, 0).isEmpty(),
        "a disabled scatter still returned sites"
    );
  }

  @Test
  void sitesKeepRoughlyTheirConfiguredDensity() {
    // 6000 blocks around the origin is roughly 434 cells at the default spacing; at 80% chance
    // all-land siting should keep a solid share of them. This is the observable-coverage guard:
    // both historical world-engine failures shipped looking correct and were only catchable by
    // measuring, so the scatter gets the same treatment.
    int found = around(LandLookup.EVERYWHERE).size();
    int cells = (int) Math.pow(2.0 * RADIUS / (double) config.getSpacing(), 2);

    assertTrue(found > cells / 2, "far fewer sites than configured: " + found + " of " + cells);
    assertTrue(found <= cells, "more sites than there are cells: " + found + " of " + cells);
  }

  private List<StructureSite> around(LandLookup land) {
    return StructureSites.around(config, structures, land, SEED, 0, 0, RADIUS);
  }
}
