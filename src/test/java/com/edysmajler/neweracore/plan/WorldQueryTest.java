package com.edysmajler.neweracore.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.noise.NoiseFields;
import com.edysmajler.neweracore.world.structures.StructureDefinition;
import com.edysmajler.neweracore.world.structures.StructureField;
import com.edysmajler.neweracore.world.structures.StructureManager;
import com.edysmajler.neweracore.world.structures.StructureSite;
import com.edysmajler.neweracore.world.structures.StructureSites;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import com.edysmajler.neweracore.world.towns.TownSites;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldQueryTest {

  private static final long SEED = 0x5EEDL;
  private static final int RADIUS = 4000;

  /** A structure that exists only to be sited; nothing here ever builds one. */
  private record Wreck(String id, int radius, double weight) implements StructureDefinition {
    @Override
    public void place(StructureField field, StructureSite site) {
      throw new UnsupportedOperationException("siting only");
    }
  }

  private static WorldQuery query(LandLookup land) {
    WorldEngineConfig config = new WorldEngineConfig();

    return new WorldQuery(
        config,
        new NoiseFields(SEED, config.getNoise()),
        new StructureManager(List.of(new Wreck("wreck", 64, 1.0))),
        land,
        SEED
    );
  }

  @Test
  @DisplayName("the query reports exactly what the siting systems report")
  void agreesWithTheSitingSystems() {
    // The whole value of the seam is that it cannot drift from the systems it wraps: a wiring
    // mistake here would put planned towns somewhere the plugin will not build them.
    WorldEngineConfig config = new WorldEngineConfig();
    StructureManager structures = new StructureManager(List.of(new Wreck("wreck", 64, 1.0)));
    LandLookup land = LandLookup.EVERYWHERE;

    List<PlannedSite> sites = query(land).sitesWithin(0, 0, RADIUS);

    long structureCount = sites.stream()
        .filter(site -> site.kind() == PlannedSite.SiteKind.STRUCTURE)
        .count();
    long townCount = sites.stream()
        .filter(site -> site.kind() == PlannedSite.SiteKind.TOWN)
        .count();

    assertEquals(
        StructureSites.around(config.getStructures(), structures, land, SEED, 0, 0, RADIUS).size(),
        structureCount
    );
    assertEquals(
        TownSites.around(config.getTowns(), land, SEED, 0, 0, RADIUS).size(),
        townCount
    );
  }

  @Test
  @DisplayName("a world of open water holds no sites at all")
  void nothingIsSitedAtSea() {
    List<PlannedSite> sites = query((x, z) -> false).sitesWithin(0, 0, RADIUS);

    assertTrue(sites.isEmpty(), "expected no sites at sea, got " + sites.size());
  }

  @Test
  @DisplayName("structures carry the id, footprint and facing they will be built with")
  void sitesAreFullyDescribed() {
    List<PlannedSite> sites = query(LandLookup.EVERYWHERE).sitesWithin(0, 0, RADIUS).stream()
        .filter(site -> site.kind() == PlannedSite.SiteKind.STRUCTURE)
        .toList();

    assertFalse(sites.isEmpty(), "the default spacing should site something within " + RADIUS);

    for (PlannedSite site : sites) {
      assertEquals("wreck", site.id());
      assertEquals(64, site.radius());
      assertTrue(site.rotation() >= 0 && site.rotation() <= 3, "rotation " + site.rotation());
    }
  }

  @Test
  @DisplayName("the noise fields answer as percentiles, which is what thresholds assume")
  void fieldsAreCalibrated() {
    WorldQuery query = query(LandLookup.EVERYWHERE);
    int high = 0;
    int samples = 0;

    for (int x = -4000; x <= 4000; x += 250) {
      for (int z = -4000; z <= 4000; z += 250) {
        double value = query.corruptionAt(x, z);
        assertTrue(value >= 0.0 && value <= 1.0, "out of range: " + value);
        samples++;
        if (value > 0.8) {
          high++;
        }
      }
    }

    // Roughly a fifth of the world should sit above the 0.8 percentile. An uncalibrated field
    // clusters around 0.5 and would put almost nothing here, which is the bug that once made
    // craters vanish entirely.
    double share = high / (double) samples;
    assertTrue(share > 0.1 && share < 0.3, "expected about a fifth above 0.8, got " + share);
  }

  @Test
  @DisplayName("the zone comes from the chunk, as the engine resolves it")
  void zoneIsResolvedPerChunk() {
    WorldQuery query = query(LandLookup.EVERYWHERE);

    // Two blocks in the same chunk must agree, or the planner would show a smoother world than
    // the one the engine builds
    assertEquals(query.zoneAt(0, 0).level(), query.zoneAt(15, 15).level());
    assertEquals(query.zoneAt(0, 0).intensity(), query.zoneAt(15, 15).intensity());
  }
}
