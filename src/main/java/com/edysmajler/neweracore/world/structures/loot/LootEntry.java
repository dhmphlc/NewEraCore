package com.edysmajler.neweracore.world.structures.loot;

import java.util.Random;
import org.bukkit.Material;

/**
 * One weighted thing a pool can produce: an item with its count, wear, and enchantment ranges —
 * or deliberately nothing.
 *
 * <p>"Nothing" is a real entry rather than a pool-level chance because that is how the odds stay
 * readable: a sidearm pool that reads {@code crossbow 3, sword 2, nothing 5} says at a glance that
 * half the wrecks have no weapon at all, without a second knob that multiplies against the
 * weights. Vanilla loot tables model it the same way ({@code minecraft:empty}).
 *
 * <p>Wear matters more than it looks: a pristine crossbow in a burnt-out wreck is the fresh-timber
 * problem in a chest — loot that survived the end of the world should look like it did.
 */
public final class LootEntry {

  private final Material material;
  private final double weight;
  private final int countMin;
  private final int countMax;
  private final double wearMin;
  private final double wearMax;
  private final int enchantMin;
  private final int enchantMax;

  /**
   * Creates an entry with every range spelled out.
   *
   * @param material the item, or null for a "nothing" entry
   * @param weight this entry's share of the pool's draw, positive
   * @param countMin smallest stack rolled, at least 1
   * @param countMax largest stack rolled, at least countMin
   * @param wearMin least durability lost, 0 to 1
   * @param wearMax most durability lost, wearMin to 1; 0 means always pristine
   * @param enchantMin fewest enchanting levels, 0 or more
   * @param enchantMax most enchanting levels, enchantMin or more; 0 means never enchanted
   * @throws IllegalArgumentException when a range is inverted or a bound is out of sense
   */
  public LootEntry(
      Material material,
      double weight,
      int countMin,
      int countMax,
      double wearMin,
      double wearMax,
      int enchantMin,
      int enchantMax
  ) {
    if (weight <= 0.0) {
      throw new IllegalArgumentException("Loot entry weight must be positive");
    }
    if (countMin < 1 || countMax < countMin) {
      throw new IllegalArgumentException("Loot entry count range is invalid");
    }
    if (wearMin < 0.0 || wearMax > 1.0 || wearMax < wearMin) {
      throw new IllegalArgumentException("Loot entry wear range is invalid");
    }
    if (enchantMin < 0 || enchantMax < enchantMin) {
      throw new IllegalArgumentException("Loot entry enchant range is invalid");
    }

    this.material = material;
    this.weight = weight;
    this.countMin = countMin;
    this.countMax = countMax;
    this.wearMin = wearMin;
    this.wearMax = wearMax;
    this.enchantMin = enchantMin;
    this.enchantMax = enchantMax;
  }

  /**
   * Creates a single-item entry with no wear and no enchantments.
   *
   * @param material the item
   * @param weight this entry's share of the pool's draw
   * @return the entry
   */
  public static LootEntry of(Material material, double weight) {
    return new LootEntry(material, weight, 1, 1, 0.0, 0.0, 0, 0);
  }

  /**
   * Creates an entry rolling a stack size in a range.
   *
   * @param material the item
   * @param weight this entry's share of the pool's draw
   * @param countMin smallest stack
   * @param countMax largest stack
   * @return the entry
   */
  public static LootEntry of(Material material, double weight, int countMin, int countMax) {
    return new LootEntry(material, weight, countMin, countMax, 0.0, 0.0, 0, 0);
  }

  /**
   * Creates an entry that produces nothing when drawn.
   *
   * @param weight this entry's share of the pool's draw
   * @return the entry
   */
  public static LootEntry nothing(double weight) {
    return new LootEntry(null, weight, 1, 1, 0.0, 0.0, 0, 0);
  }

  /**
   * Returns a copy of this entry whose item arrives with durability already lost.
   *
   * @param min least share of durability gone
   * @param max most share of durability gone
   * @return the worn entry
   */
  public LootEntry worn(double min, double max) {
    return new LootEntry(material, weight, countMin, countMax, min, max, enchantMin, enchantMax);
  }

  /**
   * Returns a copy of this entry whose item is enchanted like an enchanting-table roll.
   *
   * @param min fewest levels
   * @param max most levels
   * @return the enchanted entry
   */
  public LootEntry enchanted(int min, int max) {
    return new LootEntry(material, weight, countMin, countMax, wearMin, wearMax, min, max);
  }

  /**
   * Returns this entry's share of the pool's draw.
   *
   * @return the weight
   */
  public double weight() {
    return weight;
  }

  /**
   * Rolls this entry into a concrete item, or nothing.
   *
   * @param random the site-seeded source
   * @return the rolled item, or null for a "nothing" entry
   */
  public ItemSpec roll(Random random) {
    if (material == null) {
      return null;
    }

    int count = countMin + (countMax > countMin ? random.nextInt(countMax - countMin + 1) : 0);

    double wear = 0.0;
    if (wearMax > 0.0) {
      wear = wearMin + random.nextDouble() * (wearMax - wearMin);
    }

    int levels = 0;
    if (enchantMax > 0) {
      levels = enchantMin
          + (enchantMax > enchantMin ? random.nextInt(enchantMax - enchantMin + 1) : 0);
    }

    return new ItemSpec(material, count, wear, levels);
  }
}
