package com.edysmajler.neweracore.world.biome;

import com.edysmajler.neweracore.world.ash.AshPalette;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * Savanna under ash: dry ground already, now dust-grey and bare.
 */
public class SavannaTransformer extends AbstractBiomeTransformer {

  private static final AshPalette PALETTE = new AshPalette(
      Material.PALE_MOSS_CARPET,
      Material.DIRT_PATH,
      Material.COARSE_DIRT,
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
    return "savanna";
  }

  @Override
  public Set<Biome> biomes() {
    return Set.of(Biome.SAVANNA, Biome.SAVANNA_PLATEAU, Biome.WINDSWEPT_SAVANNA);
  }

  @Override
  public AshPalette palette() {
    return PALETTE;
  }
}
