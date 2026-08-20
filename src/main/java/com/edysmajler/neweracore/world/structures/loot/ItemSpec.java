package com.edysmajler.neweracore.world.structures.loot;

import org.bukkit.Material;

/**
 * One rolled item, before it becomes a Bukkit {@code ItemStack}.
 *
 * <p>The roll layer stops here on purpose: everything that <em>decides</em> loot — which entry,
 * how many, how worn — works on plain values and stays unit-testable, while the one class that
 * needs a live server ({@link LootStocker}) only turns finished decisions into items. Same seam as
 * {@code LandLookup}: the Bukkit call is confined, the judgement is not.
 *
 * @param material what the item is
 * @param count how many, at least one
 * @param wear durability already lost, 0 (pristine) to just under 1; ignored for items without
 *     durability
 * @param enchantLevels enchanting-table levels to roll random enchantments with, 0 for none
 */
public record ItemSpec(Material material, int count, double wear, int enchantLevels) {}
