package com.edysmajler.neweracore.world.biome;

import com.edysmajler.neweracore.world.ash.AshPalette;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * Jungle under ash: the densest canopy, so the most standing deadwood once it dies.
 */
public class JungleTransformer extends AbstractBiomeTransformer {

  private static final AshPalette PALETTE = new AshPalette(
      Material.PALE_MOSS_CARPET,
      Material.DIRT_PATH,
      Material.PALE_MOSS_BLOCK,
      Material.TUFF,
      Material.GRAVEL,
      Material.MUD,
      Material.POLISHED_BASALT,
      Material.BASALT,
      Material.DEAD_BUSH,
      List.of(Material.TUFF, Material.GRAVEL, Material.CLAY, Material.COARSE_DIRT)
  );

  @Override
  public String name() {
    return "jungle";
  }

  @Override
  public Set<Biome> biomes() {
    return Set.of(Biome.JUNGLE, Biome.SPARSE_JUNGLE, Biome.BAMBOO_JUNGLE);
  }

  @Override
  public AshPalette palette() {
    return PALETTE;
  }

  @Override
  protected boolean groveFloorsStayGreen() {
    // Dense forest: a green floor here sits under a surviving canopy, not in open country
    return true;
  }
}
