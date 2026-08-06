package com.edysmajler.neweracore.world.biome;

import com.edysmajler.neweracore.world.ash.AshPalette;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * Desert, badlands, and stony shore under ash.
 *
 * <p>The sand stays sand — the point is to bury the biome, not replace it — but the ash carpet lies
 * over it like everywhere else, which is what ties these regions into the same world as the
 * forests.
 */
public class DesertTransformer extends AbstractBiomeTransformer {

  private static final AshPalette PALETTE = new AshPalette(
      Material.PALE_MOSS_CARPET,
      Material.DIRT_PATH,
      Material.LIGHT_GRAY_TERRACOTTA,
      Material.TUFF,
      Material.GRAVEL,
      Material.CLAY,
      Material.POLISHED_BASALT,
      Material.BASALT,
      Material.DEAD_BUSH,
      List.of(Material.TUFF, Material.GRAVEL, Material.ANDESITE, Material.LIGHT_GRAY_TERRACOTTA)
  );

  @Override
  public String name() {
    return "desert";
  }

  @Override
  public Set<Biome> biomes() {
    return Set.of(
        Biome.DESERT,
        Biome.BADLANDS,
        Biome.WOODED_BADLANDS,
        Biome.ERODED_BADLANDS,
        Biome.STONY_SHORE
    );
  }

  @Override
  public AshPalette palette() {
    return PALETTE;
  }

  @Override
  protected boolean hasTrees() {
    return false;
  }
}
