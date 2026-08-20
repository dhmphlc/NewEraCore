package com.edysmajler.neweracore.world.structures.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

@SuppressFBWarnings(
    value = "PREDICTABLE_RANDOM",
    justification = "Seeded java.util.Random is the point under test: loot must be deterministic."
)
class LootTableTest {

  @Test
  void sameSeedRollsIdenticalLoot() {
    // Loot is part of a site's identity: everything else about a wreck is fixed by the seed, so a
    // chest that restocks differently per placement attempt would be the one nondeterministic part
    LootTable table = LootTables.builtIn(LootTables.MILITARY);

    List<ItemSpec> first = table.roll(new Random(42));
    List<ItemSpec> second = table.roll(new Random(42));

    assertEquals(first, second);
  }

  @Test
  void rollsStayInsideThePoolRange() {
    LootPool pool = new LootPool(2, 4, List.of(LootEntry.of(Material.COAL, 1)));
    Set<Integer> sizes = new HashSet<>();
    Random random = new Random(7);

    for (int i = 0; i < 500; i++) {
      List<ItemSpec> out = new ArrayList<>();
      pool.rollInto(out, random);
      sizes.add(out.size());
    }

    assertEquals(Set.of(2, 3, 4), sizes);
  }

  @Test
  void countsStayInsideTheEntryRange() {
    LootEntry entry = LootEntry.of(Material.ARROW, 1, 6, 14);
    Random random = new Random(11);

    for (int i = 0; i < 500; i++) {
      ItemSpec spec = entry.roll(random);
      assertTrue(spec.count() >= 6 && spec.count() <= 14);
    }
  }

  @Test
  void weightsShapeTheDraw() {
    LootPool pool = new LootPool(1, 1, List.of(
        LootEntry.of(Material.COAL, 9),
        LootEntry.of(Material.DIAMOND, 1)
    ));
    Random random = new Random(13);

    int diamonds = 0;
    for (int i = 0; i < 2000; i++) {
      List<ItemSpec> out = new ArrayList<>();
      pool.rollInto(out, random);
      if (out.get(0).material() == Material.DIAMOND) {
        diamonds++;
      }
    }

    // Expected share is 10%; anything wildly off means the weighted walk is broken
    assertTrue(diamonds > 100 && diamonds < 300, "diamond share was " + diamonds + "/2000");
  }

  @Test
  void nothingEntriesLeaveRealGaps() {
    // Rarity is modelled as a weighted blank beside the prize, so the odds read straight off the
    // entry list; a pool where "nothing" never lands would quietly double every prize rate
    LootPool pool = new LootPool(1, 1, List.of(
        LootEntry.of(Material.GOLDEN_APPLE, 1),
        LootEntry.nothing(3)
    ));
    Random random = new Random(17);

    int empty = 0;
    for (int i = 0; i < 1000; i++) {
      List<ItemSpec> out = new ArrayList<>();
      pool.rollInto(out, random);
      if (out.isEmpty()) {
        empty++;
      }
    }

    assertTrue(empty > 600 && empty < 900, "empty share was " + empty + "/1000");
  }

  @Test
  void wearAndEnchantRangesAreRespected() {
    LootEntry worn = LootEntry.of(Material.CROSSBOW, 1).worn(0.2, 0.7);
    LootEntry enchanted = LootEntry.of(Material.BOOK, 1).enchanted(10, 25);
    Random random = new Random(19);

    for (int i = 0; i < 200; i++) {
      ItemSpec wornSpec = worn.roll(random);
      assertTrue(wornSpec.wear() >= 0.2 && wornSpec.wear() <= 0.7);
      assertEquals(0, wornSpec.enchantLevels());

      ItemSpec enchantedSpec = enchanted.roll(random);
      assertTrue(enchantedSpec.enchantLevels() >= 10 && enchantedSpec.enchantLevels() <= 25);
      assertEquals(0.0, enchantedSpec.wear());
    }
  }

  @Test
  void invalidShapesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> LootEntry.of(Material.COAL, 0));
    assertThrows(IllegalArgumentException.class, () -> LootEntry.of(Material.COAL, 1, 3, 2));
    assertThrows(IllegalArgumentException.class,
        () -> new LootPool(2, 1, List.of(LootEntry.of(Material.COAL, 1))));
    assertThrows(IllegalArgumentException.class, () -> new LootPool(1, 1, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new LootTable("empty", List.of()));
  }
}
