package com.edysmajler.neweracore.world.feature;

import com.edysmajler.neweracore.world.ash.AshPalette;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

/**
 * Builds burnt wood.
 *
 * <p>Stripped logs are pale tan, which reads as fresh timber rather than as something that burned.
 * The
 * palette uses basalt instead: polished basalt has a vertical grain that looks like a charred
 * trunk,
 * and plain basalt takes an axis so a fallen one can lie on its side.
 */
public final class CharredWood {

  private CharredWood() {}

  /**
   * Returns a standing charred trunk block.
   *
   * @param palette the biome's materials
   * @return the block data for a standing trunk
   */
  public static BlockData standing(AshPalette palette) {
    return orient(palette.charredWood(), Axis.Y);
  }

  /**
   * Returns a fallen charred trunk block lying along an axis.
   *
   * @param palette the biome's materials
   * @param axis the axis the trunk lies along
   * @return the block data for a fallen trunk
   */
  public static BlockData fallen(AshPalette palette, Axis axis) {
    return orient(palette.fallenWood(), axis);
  }

  private static BlockData orient(Material material, Axis axis) {
    BlockData data = Bukkit.createBlockData(material);

    if (data instanceof Orientable orientable) {
      orientable.setAxis(axis);
      return orientable;
    }

    return data;
  }
}
