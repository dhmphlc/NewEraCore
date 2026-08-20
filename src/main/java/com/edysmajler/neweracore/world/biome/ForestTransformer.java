package com.edysmajler.neweracore.world.biome;

import com.edysmajler.neweracore.world.ash.AshPalette;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * Oak and birch forest under ash.
 *
 * <p>The reference palette: pale ash carpet over dust-grey ground, basalt snags where the trees
 * stood.
 */
public class ForestTransformer extends AbstractBiomeTransformer {

  private static final AshPalette PALETTE = new AshPalette(
      Material.PALE_MOSS_CARPET,
      Material.DIRT_PATH,
      Material.PALE_MOSS_BLOCK,
      Material.TUFF,
      Material.GRAVEL,
      Material.PACKED_MUD,
      Material.POLISHED_BASALT,
      Material.BASALT,
      Material.DEAD_BUSH,
      List.of(Material.TUFF, Material.GRAVEL, Material.COBBLED_DEEPSLATE, Material.COARSE_DIRT)
  );

  @Override
  public String name() {
    return "forest";
  }

  @Override
  public Set<Biome> biomes() {
    return Set.of(
        Biome.FOREST,
        Biome.FLOWER_FOREST,
        Biome.BIRCH_FOREST,
        Biome.OLD_GROWTH_BIRCH_FOREST,
        Biome.DARK_FOREST,
        Biome.PALE_GARDEN,
        Biome.WINDSWEPT_FOREST
    );
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
