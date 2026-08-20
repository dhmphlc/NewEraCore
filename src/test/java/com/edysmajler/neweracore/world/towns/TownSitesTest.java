package com.edysmajler.neweracore.world.towns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.TownsConfig;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownSitesTest {

  private static final long SEED = 987654321L;

  private final TownsConfig config = new TownsConfig();

  @Test
  void townsExistAtDefaults() {
    // Every other assertion here is vacuous on an empty map, and an empty map is itself the bug
    assertFalse(TownSites.around(config, LandLookup.EVERYWHERE, SEED, 0, 0, 8192).isEmpty());
  }

  @Test
  void sameSeedResolvesTheSameTowns() {
    assertEquals(
        TownSites.around(config, LandLookup.EVERYWHERE, SEED, 0, 0, 8192),
        TownSites.around(config, LandLookup.EVERYWHERE, SEED, 0, 0, 8192));
  }

  @Test
  void everyChunkTheFootprintTouchesSeesTheTown() {
    // The window invariant structure sites guard too: a chunk that misses a town it touches may
    // be the last of the footprint to generate, and then the town never places
    List<TownSite> towns = TownSites.around(config, LandLookup.EVERYWHERE, SEED, 0, 0, 8192);

    for (TownSite town : towns) {
      int minChunkX = (town.centerX() - town.radius()) >> 4;
      int maxChunkX = (town.centerX() + town.radius()) >> 4;
      int minChunkZ = (town.centerZ() - town.radius()) >> 4;
      int maxChunkZ = (town.centerZ() + town.radius()) >> 4;

      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
          if (!town.touchesChunk(chunkX, chunkZ)) {
            continue;
          }
          assertTrue(
              TownSites.near(config, LandLookup.EVERYWHERE, SEED, chunkX, chunkZ).contains(town),
              "chunk " + chunkX + ", " + chunkZ + " misses a town whose footprint touches it");
        }
      }
    }
  }

  @Test
  void townsNeedLand() {
    LandLookup allSea = (blockX, blockZ) -> false;

    assertTrue(TownSites.around(config, allSea, SEED, 0, 0, 8192).isEmpty(),
        "a town stood at sea");
  }

  @Test
  void streetsAreUnitHeadings() {
    for (TownSite town : TownSites.around(config, LandLookup.EVERYWHERE, SEED, 0, 0, 8192)) {
      for (TownSite.Heading street : town.streets()) {
        assertEquals(1.0, Math.hypot(street.x(), street.z()), 1e-6);
      }
    }
  }

  @Test
  void aroundSortsNearestFirst() {
    List<TownSite> towns = TownSites.around(config, LandLookup.EVERYWHERE, SEED, 500, 500, 8192);
    assertTrue(towns.size() >= 2, "too few towns to check ordering");

    for (int i = 1; i < towns.size(); i++) {
      assertTrue(towns.get(i - 1).distanceTo(500, 500) <= towns.get(i).distanceTo(500, 500));
    }
  }

  @Test
  void townsCanBeDisabledWhole() {
    TownsConfig off = new TownsConfig() {
      @Override
      public boolean isEnabled() {
        return false;
      }
    };

    assertTrue(TownSites.near(off, LandLookup.EVERYWHERE, SEED, 40, 40).isEmpty());
    assertTrue(TownSites.around(off, LandLookup.EVERYWHERE, SEED, 0, 0, 8192).isEmpty());
  }
}
