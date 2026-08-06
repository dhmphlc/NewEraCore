package com.edysmajler.neweracore.world.biome;

import com.edysmajler.neweracore.world.ash.AshPalette;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * Swamp under ash: wet ground turns to grey mud, and the ash settles into it.
 */
public class SwampTransformer extends AbstractBiomeTransformer {

  private static final AshPalette PALETTE = new AshPalette(
      Material.PALE_MOSS_CARPET,
      Material.MUD,
      Material.PACKED_MUD,
      Material.TUFF,
      Material.GRAVEL,
      Material.CLAY,
      Material.POLISHED_BASALT,
      Material.BASALT,
      Material.DEAD_BUSH,
      List.of(Material.PACKED_MUD, Material.GRAVEL, Material.CLAY, Material.TUFF)
  );

  @Override
  public String name() {
    return "swamp";
  }

  @Override
  public Set<Biome> biomes() {
    return Set.of(Biome.SWAMP, Biome.MANGROVE_SWAMP);
  }

  @Override
  public AshPalette palette() {
    return PALETTE;
  }
}
