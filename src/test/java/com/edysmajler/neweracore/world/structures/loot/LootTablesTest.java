package com.edysmajler.neweracore.world.structures.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.StructuresConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

@SuppressFBWarnings(
    value = "PREDICTABLE_RANDOM",
    justification = "Seeded java.util.Random is the point under test: loot must be deterministic."
)
class LootTablesTest {

  private static final Logger LOGGER = Logger.getLogger("LootTablesTest");

  private static StructuresConfig config(String yaml) throws Exception {
    return new ObjectMapper(new YAMLFactory()).readValue(yaml, StructuresConfig.class);
  }

  @Test
  void builtInsAlwaysExist() {
    // A fresh install must stock every wreck before the owner writes a line of config
    assertNotNull(LootTables.builtIn(LootTables.MILITARY));
    assertNotNull(LootTables.builtIn(LootTables.CIVILIAN));
    assertNotNull(LootTables.builtIn(LootTables.HARDWARE));
  }

  @Test
  void builtInTablesActuallyProduceLoot() {
    // Weighted blanks make an all-empty chest possible in one roll, but a table that averages
    // near-empty ships a world of bare chests while looking perfectly wired
    for (String id : List.of(LootTables.MILITARY, LootTables.CIVILIAN, LootTables.HARDWARE)) {
      Random random = new Random(1234);
      int items = 0;

      for (int i = 0; i < 100; i++) {
        items += LootTables.builtIn(id).roll(random).size();
      }

      assertTrue(items >= 300, id + " averaged under 3 items a chest: " + items + "/100 rolls");
    }
  }

  @Test
  void noneAndUnknownResolveToNoLoot() throws Exception {
    LootTables tables = LootTables.load(config("{}"), LOGGER);

    assertNull(tables.resolve("none", LOGGER));
    assertNull(tables.resolve("no_such_table", LOGGER));
    assertNotNull(tables.resolve("military", LOGGER));
  }

  @Test
  void configTablesJoinTheRegistry() throws Exception {
    LootTables tables = LootTables.load(config(
        """
        loot:
          medical:
            - rolls: 2-3
              entries:
                - {item: paper, weight: 3, count: 2-6}
                - {item: golden_apple}
                - {item: nothing, weight: 2}
        """), LOGGER);

    LootTable medical = tables.resolve("medical", LOGGER);
    assertNotNull(medical);

    List<ItemSpec> rolled = medical.roll(new Random(5));
    for (ItemSpec spec : rolled) {
      assertTrue(spec.material() == Material.PAPER || spec.material() == Material.GOLDEN_APPLE);
    }
  }

  @Test
  void unknownItemsCostOneEntryNotTheTable() throws Exception {
    LootTables tables = LootTables.load(config(
        """
        loot:
          scrap:
            - rolls: 1
              entries:
                - {item: definitely_not_an_item, weight: 100}
                - {item: iron_ingot}
        """), LOGGER);

    LootTable scrap = tables.resolve("scrap", LOGGER);
    assertNotNull(scrap);

    // The typo entry is gone, so every roll lands on the one entry that parsed
    List<ItemSpec> rolled = scrap.roll(new Random(3));
    assertEquals(1, rolled.size());
    assertEquals(Material.IRON_INGOT, rolled.get(0).material());
  }

  @Test
  void configReplacesBuiltInsWhole() throws Exception {
    LootTables tables = LootTables.load(config(
        """
        loot:
          military:
            - rolls: 1
              entries:
                - {item: stick}
        """), LOGGER);

    List<ItemSpec> rolled = tables.resolve("military", LOGGER).roll(new Random(9));

    assertEquals(1, rolled.size());
    assertEquals(Material.STICK, rolled.get(0).material());
  }

  @Test
  void wearAndCountAreClampedToSense() throws Exception {
    LootTables tables = LootTables.load(config(
        """
        loot:
          bent:
            - rolls: 1
              entries:
                - {item: iron_pickaxe, count: 500-900, wear: 0.5-3.0}
        """), LOGGER);

    ItemSpec spec = tables.resolve("bent", LOGGER).roll(new Random(1)).get(0);

    assertTrue(spec.count() <= 64);
    assertTrue(spec.wear() <= 0.95, "an item must never arrive already broken");
    assertFalse(spec.wear() < 0.5);
  }
}
