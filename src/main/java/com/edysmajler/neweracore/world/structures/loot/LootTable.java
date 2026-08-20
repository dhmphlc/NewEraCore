package com.edysmajler.neweracore.world.structures.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What one kind of container holds: a fixed list of pools, rolled fresh for every placement.
 *
 * <p>This is the plugin's own take on the vanilla loot-table shape — pools, rolls, weighted
 * entries — rather than the server's {@code org.bukkit.loot.LootTable}, for one load-bearing
 * reason: a Bukkit loot table is resolved by namespaced key from the datapack registry, so custom
 * tables would make every install carry a datapack beside the plugin, and a table handed to
 * {@code Lootable.setLootTable} that is not in that registry silently dies with the chunk save.
 * Rolling here and writing plain items keeps the whole system inside the JAR and — because every
 * roll comes off the site-seeded stream — deterministic per site, like everything else the
 * scatter builds.
 */
public final class LootTable {

  private final String id;
  private final List<LootPool> pools;

  /**
   * Creates a table.
   *
   * @param id the name templates refer to this table by
   * @param pools the pools, rolled in order
   * @throws IllegalArgumentException when no pools are given
   */
  public LootTable(String id, List<LootPool> pools) {
    if (pools.isEmpty()) {
      throw new IllegalArgumentException("Loot table " + id + " needs at least one pool");
    }

    this.id = id;
    this.pools = List.copyOf(pools);
  }

  /**
   * Returns the name templates refer to this table by.
   *
   * @return the id
   */
  public String id() {
    return id;
  }

  /**
   * Rolls every pool once.
   *
   * @param random the site-seeded source
   * @return the rolled items, possibly empty
   */
  public List<ItemSpec> roll(Random random) {
    List<ItemSpec> out = new ArrayList<>();

    for (LootPool pool : pools) {
      pool.rollInto(out, random);
    }

    return out;
  }
}
