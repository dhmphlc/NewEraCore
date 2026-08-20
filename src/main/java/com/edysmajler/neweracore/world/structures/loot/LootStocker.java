package com.edysmajler.neweracore.world.structures.loot;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Turns a rolled table into items in a container.
 *
 * <p>This is the only class in the loot system that touches a live server — item meta and the
 * enchanting roll both need one — so everything above it stays unit-testable. See {@link ItemSpec}
 * for the seam.
 *
 * <p>Items land in shuffled slots rather than packed from slot zero. Packed loot is the
 * machine-filled look — the chest equivalent of a painted-on ashfall — and vanilla scatters its
 * chest loot for the same reason.
 */
public final class LootStocker {

  private LootStocker() {}

  /**
   * Rolls a table into a container's free slots.
   *
   * <p>Existing items are left alone; whatever does not fit is dropped from the roll. Both come to
   * nothing in practice — the scatter only stocks empty containers — but a partly filled chest
   * must never be an error.
   *
   * @param inventory the container's own inventory (a chest's block inventory, not the double-wide
   *     view)
   * @param table what this container holds
   * @param random the site-seeded source, so a site's loot is as fixed as its silhouette
   */
  public static void stock(Inventory inventory, LootTable table, Random random) {
    List<ItemSpec> specs = table.roll(random);
    if (specs.isEmpty()) {
      return;
    }

    List<Integer> free = new ArrayList<>();
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      if (inventory.getItem(slot) == null) {
        free.add(slot);
      }
    }
    Collections.shuffle(free, random);

    int next = 0;
    for (ItemSpec spec : specs) {
      if (next >= free.size()) {
        return;
      }
      inventory.setItem(free.get(next++), build(spec, random));
    }
  }

  @SuppressFBWarnings(
      value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT",
      justification = "ItemStack.setItemMeta mutates the stack; the API jar delegates to a "
          + "server-side handle, which hides the side effect from the analysis."
  )
  private static ItemStack build(ItemSpec spec, Random random) {
    ItemStack item = new ItemStack(
        spec.material(), Math.min(spec.count(), spec.material().getMaxStackSize()));

    if (spec.enchantLevels() > 0) {
      // The enchanting-table roll vanilla loot uses; a plain book comes back an enchanted book
      item = Bukkit.getItemFactory()
          .enchantWithLevels(item, spec.enchantLevels(), false, random);
    }

    short maxDurability = spec.material().getMaxDurability();
    if (spec.wear() > 0.0 && maxDurability > 0
        && item.getItemMeta() instanceof Damageable meta) {
      meta.setDamage((int) (maxDurability * spec.wear()));
      item.setItemMeta(meta);
    }

    return item;
  }
}
