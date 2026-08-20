package com.edysmajler.neweracore.world.biome;

import com.edysmajler.neweracore.world.ash.AshPalette;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * Spruce country under ash: taiga, groves, cherry groves.
 *
 * <p>Colder and stonier than the lowland forest, so tuff leads the ground and deepslate the debris.
 */
public class TaigaTransformer extends AbstractBiomeTransformer {

  private static final AshPalette PALETTE = new AshPalette(
      Material.PALE_MOSS_CARPET,
      Material.TUFF,
      Material.PALE_MOSS_BLOCK,
      Material.COBBLED_DEEPSLATE,
      Material.GRAVEL,
      Material.CLAY,
      Material.POLISHED_BASALT,
      Material.BASALT,
      Material.DEAD_BUSH,
      List.of(Material.COBBLED_DEEPSLATE, Material.TUFF, Material.GRAVEL, Material.ANDESITE)
  );

  @Override
  public String name() {
    return "taiga";
  }

  @Override
  public Set<Biome> biomes() {
    return Set.of(
        Biome.TAIGA,
        Biome.SNOWY_TAIGA,
        Biome.OLD_GROWTH_PINE_TAIGA,
        Biome.OLD_GROWTH_SPRUCE_TAIGA,
        Biome.GROVE,
        Biome.CHERRY_GROVE
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
