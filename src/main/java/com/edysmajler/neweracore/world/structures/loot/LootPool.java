package com.edysmajler.neweracore.world.structures.loot;

import java.util.List;
import java.util.Random;

/**
 * One themed draw within a table: roll a number of times, pick a weighted entry each time.
 *
 * <p>Pools are the structure that keeps a chest coherent. One flat list of everything a wreck
 * could hold either yields six golden apples and no food, or needs weights so skewed nothing rare
 * ever appears. Split into pools — bulk salvage, rations, a sidearm, one prize — each theme rolls
 * its own small count, so every chest gets a believable spread and the rare pool stays rare
 * without starving the common ones. Vanilla's chest tables are built exactly this way.
 */
public final class LootPool {

  private final int rollsMin;
  private final int rollsMax;
  private final List<LootEntry> entries;
  private final double totalWeight;

  /**
   * Creates a pool.
   *
   * @param rollsMin fewest draws, 0 or more — a 0 lower bound is how a pool sits empty sometimes
   * @param rollsMax most draws, at least rollsMin
   * @param entries the weighted entries, at least one
   * @throws IllegalArgumentException when the rolls range is inverted or no entries are given
   */
  public LootPool(int rollsMin, int rollsMax, List<LootEntry> entries) {
    if (rollsMin < 0 || rollsMax < rollsMin) {
      throw new IllegalArgumentException("Loot pool rolls range is invalid");
    }
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("Loot pool needs at least one entry");
    }

    this.rollsMin = rollsMin;
    this.rollsMax = rollsMax;
    this.entries = List.copyOf(entries);

    double weight = 0.0;
    for (LootEntry entry : entries) {
      weight += entry.weight();
    }
    this.totalWeight = weight;
  }

  /**
   * Rolls this pool, appending whatever it produces.
   *
   * @param out the items rolled so far
   * @param random the site-seeded source
   */
  public void rollInto(List<ItemSpec> out, Random random) {
    int rolls = rollsMin + (rollsMax > rollsMin ? random.nextInt(rollsMax - rollsMin + 1) : 0);

    for (int i = 0; i < rolls; i++) {
      ItemSpec spec = pick(random.nextDouble()).roll(random);
      if (spec != null) {
        out.add(spec);
      }
    }
  }

  /**
   * Draws an entry by weight, same walk as {@code StructureManager.pick}.
   */
  private LootEntry pick(double roll) {
    double target = roll * totalWeight;
    double seen = 0.0;
    LootEntry last = null;

    for (LootEntry entry : entries) {
      last = entry;
      seen += entry.weight();
      if (target < seen) {
        return entry;
      }
    }

    return last;
  }
}
