package com.edysmajler.neweracore.world.biome;

import com.edysmajler.neweracore.world.ash.AshPalette;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * Fallback for biomes no other transformer claims, including any a future game version adds.
 */
public class DefaultTransformer extends AbstractBiomeTransformer {

  private static final AshPalette PALETTE = new AshPalette(
      Material.PALE_MOSS_CARPET,
      Material.DIRT_PATH,
      Material.PALE_MOSS_BLOCK,
      Material.TUFF,
      Material.GRAVEL,
      Material.CLAY,
      Material.POLISHED_BASALT,
      Material.BASALT,
      Material.DEAD_BUSH,
      List.of(Material.TUFF, Material.GRAVEL, Material.COARSE_DIRT, Material.ANDESITE)
  );

  @Override
  public String name() {
    return "default";
  }

  @Override
  public Set<Biome> biomes() {
    return Set.of();
  }

  @Override
  public AshPalette palette() {
    return PALETTE;
  }
}
