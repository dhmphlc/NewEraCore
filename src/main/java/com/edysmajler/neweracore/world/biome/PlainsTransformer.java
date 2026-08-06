package com.edysmajler.neweracore.world.biome;

import com.edysmajler.neweracore.world.ash.AshPalette;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * Open ground under ash: plains, meadows, windswept hills, riverbanks, beaches.
 *
 * <p>The widest open sightlines in the game, so this is where the unbroken ash field does its work.
 */
public class PlainsTransformer extends AbstractBiomeTransformer {

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
    return "plains";
  }

  @Override
  public Set<Biome> biomes() {
    return Set.of(
        Biome.PLAINS,
        Biome.SUNFLOWER_PLAINS,
        Biome.SNOWY_PLAINS,
        Biome.MEADOW,
        Biome.WINDSWEPT_HILLS,
        Biome.WINDSWEPT_GRAVELLY_HILLS,
        Biome.RIVER,
        Biome.BEACH
    );
  }

  @Override
  public AshPalette palette() {
    return PALETTE;
  }
}
