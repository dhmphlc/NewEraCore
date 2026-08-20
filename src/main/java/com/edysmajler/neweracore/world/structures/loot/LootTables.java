package com.edysmajler.neweracore.world.structures.loot;

import com.edysmajler.neweracore.config.LootEntryConfig;
import com.edysmajler.neweracore.config.LootPoolConfig;
import com.edysmajler.neweracore.config.StructuresConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.Material;

/**
 * The registry of loot tables the scatter can stock containers from.
 *
 * <p>Three tables ship built in — {@code military}, {@code civilian}, {@code hardware} — so a
 * fresh install stocks every wreck with themed loot before the server owner writes a line of
 * config. Tables defined under {@code structures.loot} in config.yml join them, and a config table
 * with a built-in's id replaces it whole, which is how the defaults get retuned: redefine the
 * table, never merge into it, so what a chest can hold is always readable from one place.
 *
 * <p>Parsing is deliberately forgiving in the same way schematic loading is: an entry naming an
 * unknown material is logged and skipped rather than failing the enable, because one typo in a
 * loot list should not cost the world its structures.
 */
public final class LootTables {

  /** Military salvage: the jet, and the default for everything that fell out of the sky. */
  public static final String MILITARY = "military";

  /** Household scavenge: cars, houses, and the default for plain templates. */
  public static final String CIVILIAN = "civilian";

  /** Workshop stock: tools and materials, assign via config. */
  public static final String HARDWARE = "hardware";

  /** The reserved id that switches stocking off for a template. */
  public static final String NONE = "none";

  /** The reserved entry item that produces nothing when drawn. */
  private static final String NOTHING = "nothing";

  private static final Map<String, LootTable> BUILT_INS = builtIns();

  private final Map<String, LootTable> byId;

  private LootTables(Map<String, LootTable> byId) {
    this.byId = byId;
  }

  /**
   * Builds the registry: the built-ins, then whatever the config defines over them.
   *
   * @param config the structures config holding the {@code loot} section
   * @param logger where skipped entries and empty tables are reported
   * @return the registry
   */
  public static LootTables load(StructuresConfig config, Logger logger) {
    Map<String, LootTable> tables = new LinkedHashMap<>(BUILT_INS);

    for (Map.Entry<String, List<LootPoolConfig>> defined : config.getLoot().entrySet()) {
      String id = defined.getKey().toLowerCase(Locale.ROOT);

      if (NONE.equals(id)) {
        logger.warning("Loot table id 'none' is reserved; skipping it");
        continue;
      }

      LootTable table = parse(id, defined.getValue(), logger);
      if (table != null) {
        tables.put(id, table);
      }
    }

    return new LootTables(tables);
  }

  /**
   * Returns the table a template asked for, or null when it asked for nothing.
   *
   * <p>Null rather than an empty table, so callers can skip the container walk entirely; an
   * unknown id resolves to nothing too, but says so — a chest silently empty because of a typo is
   * the kind of failure nobody traces back to config.yml.
   *
   * @param id the table id from the template config, possibly {@code none}
   * @param logger where an unknown id is reported
   * @return the table, or null for {@code none} and unknown ids
   */
  public LootTable resolve(String id, Logger logger) {
    String key = id.toLowerCase(Locale.ROOT);

    if (NONE.equals(key)) {
      return null;
    }

    LootTable table = byId.get(key);
    if (table == null) {
      logger.warning("Unknown loot table '" + id + "'; its containers will not be stocked");
    }

    return table;
  }

  /**
   * Returns a built-in table directly, for the built-in structures' defaults.
   *
   * @param id a built-in id
   * @return the table
   */
  public static LootTable builtIn(String id) {
    return Objects.requireNonNull(BUILT_INS.get(id), "No built-in loot table " + id);
  }

  /**
   * Parses one config-defined table, skipping what does not parse and reporting it.
   */
  private static LootTable parse(String id, List<LootPoolConfig> pools, Logger logger) {
    List<LootPool> parsed = new ArrayList<>();

    for (LootPoolConfig pool : pools) {
      List<LootEntry> entries = new ArrayList<>();

      for (LootEntryConfig entry : pool.getEntries()) {
        LootEntry built = parseEntry(id, entry, logger);
        if (built != null) {
          entries.add(built);
        }
      }

      if (entries.isEmpty()) {
        logger.warning("Loot table '" + id + "' has a pool with no usable entries; skipping it");
        continue;
      }

      double[] rolls = range(pool.getRolls());
      parsed.add(new LootPool(
          (int) clamp(rolls[0], 0, 27), (int) clamp(rolls[1], 0, 27), entries));
    }

    if (parsed.isEmpty()) {
      logger.warning("Loot table '" + id + "' has no usable pools; keeping it unregistered");
      return null;
    }

    return new LootTable(id, parsed);
  }

  private static LootEntry parseEntry(String tableId, LootEntryConfig entry, Logger logger) {
    if (NOTHING.equalsIgnoreCase(entry.getItem())) {
      return LootEntry.nothing(entry.getWeight());
    }

    Material material = Material.matchMaterial(entry.getItem());
    if (material == null) {
      logger.warning("Loot table '" + tableId + "': unknown item '" + entry.getItem()
          + "'; skipping that entry");
      return null;
    }

    double[] count = range(entry.getCount());
    double[] wear = entry.getWear() == null ? new double[] {0.0, 0.0} : range(entry.getWear());
    double[] enchant =
        entry.getEnchant() == null ? new double[] {0.0, 0.0} : range(entry.getEnchant());

    return new LootEntry(
        material,
        entry.getWeight(),
        (int) clamp(count[0], 1, 64),
        (int) clamp(count[1], 1, 64),
        clamp(wear[0], 0.0, 0.95),
        clamp(wear[1], 0.0, 0.95),
        (int) clamp(enchant[0], 0, 30),
        (int) clamp(enchant[1], 0, 30)
    );
  }

  /**
   * Parses "3" or "2-5" into an ordered pair; the config layer has already pattern-checked it.
   */
  private static double[] range(String text) {
    String[] parts = text.split("-", 2);
    double min = Double.parseDouble(parts[0].trim());
    double max = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : min;

    return max < min ? new double[] {max, min} : new double[] {min, max};
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  /**
   * The tables that ship with the plugin.
   *
   * <p>Each follows the vanilla chest-table anatomy — bulk pool, rations pool, one gear piece,
   * one long-shot prize — because that spread is what makes a chest read as somebody's kit rather
   * than a random pile. Gear arrives worn: pristine equipment in a burnt wreck is the fresh-timber
   * artefact in a chest.
   */
  private static Map<String, LootTable> builtIns() {
    Map<String, LootTable> tables = new LinkedHashMap<>();

    tables.put(MILITARY, new LootTable(MILITARY, List.of(
        new LootPool(2, 4, List.of(
            LootEntry.of(Material.IRON_INGOT, 5, 2, 5),
            LootEntry.of(Material.GUNPOWDER, 4, 2, 5),
            LootEntry.of(Material.ARROW, 4, 6, 14),
            LootEntry.of(Material.SPECTRAL_ARROW, 1, 3, 6),
            LootEntry.of(Material.STRING, 2, 1, 3),
            LootEntry.of(Material.LEATHER, 2, 1, 2)
        )),
        new LootPool(1, 2, List.of(
            LootEntry.of(Material.BREAD, 3, 1, 3),
            LootEntry.of(Material.COOKED_PORKCHOP, 2, 1, 2),
            LootEntry.of(Material.GOLDEN_CARROT, 1, 1, 2),
            LootEntry.nothing(2)
        )),
        new LootPool(1, 1, List.of(
            LootEntry.of(Material.CROSSBOW, 3).worn(0.2, 0.7),
            LootEntry.of(Material.IRON_SWORD, 2).worn(0.3, 0.8),
            LootEntry.of(Material.IRON_HELMET, 2).worn(0.3, 0.8),
            LootEntry.nothing(5)
        )),
        new LootPool(1, 1, List.of(
            LootEntry.of(Material.GOLDEN_APPLE, 4),
            LootEntry.of(Material.DIAMOND, 3, 1, 2),
            LootEntry.of(Material.BOOK, 2).enchanted(10, 25),
            LootEntry.of(Material.NAME_TAG, 2),
            LootEntry.nothing(9)
        ))
    )));

    tables.put(CIVILIAN, new LootTable(CIVILIAN, List.of(
        new LootPool(2, 4, List.of(
            LootEntry.of(Material.BREAD, 4, 1, 3),
            LootEntry.of(Material.BAKED_POTATO, 3, 1, 4),
            LootEntry.of(Material.DRIED_KELP, 2, 2, 6),
            LootEntry.of(Material.COAL, 4, 2, 6),
            LootEntry.of(Material.PAPER, 2, 1, 5),
            LootEntry.of(Material.BOOK, 2, 1, 2),
            LootEntry.of(Material.STRING, 2, 1, 3),
            LootEntry.of(Material.IRON_NUGGET, 3, 2, 7),
            LootEntry.of(Material.COPPER_INGOT, 3, 1, 4)
        )),
        new LootPool(1, 2, List.of(
            LootEntry.of(Material.LEATHER_BOOTS, 2).worn(0.2, 0.7),
            LootEntry.of(Material.FLINT_AND_STEEL, 2).worn(0.1, 0.6),
            LootEntry.of(Material.COMPASS, 1),
            LootEntry.of(Material.CLOCK, 1),
            LootEntry.nothing(4)
        )),
        new LootPool(0, 1, List.of(
            LootEntry.of(Material.EMERALD, 3, 1, 3),
            LootEntry.of(Material.IRON_INGOT, 4, 1, 3),
            LootEntry.of(Material.GOLDEN_APPLE, 1),
            LootEntry.nothing(6)
        ))
    )));

    tables.put(HARDWARE, new LootTable(HARDWARE, List.of(
        new LootPool(2, 4, List.of(
            LootEntry.of(Material.IRON_INGOT, 4, 1, 4),
            LootEntry.of(Material.COPPER_INGOT, 3, 2, 5),
            LootEntry.of(Material.REDSTONE, 2, 3, 8),
            LootEntry.of(Material.COAL, 3, 3, 8),
            LootEntry.of(Material.FLINT, 2, 2, 5),
            LootEntry.of(Material.IRON_CHAIN, 2, 1, 4),
            LootEntry.of(Material.BUCKET, 1)
        )),
        new LootPool(1, 2, List.of(
            LootEntry.of(Material.IRON_PICKAXE, 3).worn(0.3, 0.8),
            LootEntry.of(Material.IRON_SHOVEL, 3).worn(0.3, 0.8),
            LootEntry.of(Material.IRON_AXE, 2).worn(0.3, 0.8),
            LootEntry.of(Material.SHEARS, 1).worn(0.2, 0.6),
            LootEntry.nothing(4)
        )),
        new LootPool(0, 1, List.of(
            LootEntry.of(Material.DIAMOND, 2, 1, 2),
            LootEntry.of(Material.BOOK, 1).enchanted(5, 20),
            LootEntry.nothing(7)
        ))
    )));

    return tables;
  }
}
