package com.edysmajler.neweracore.world.ash;

import java.util.List;
import org.bukkit.Material;

/**
 * The materials one biome group turns into under the ashfall.
 *
 * <p>The palette is deliberately narrow and pale. An earlier version shuffled three mid-brown soils
 * per column, which is exactly what a griefer with a dirt brush produces: variety at block scale
 * reads as vandalism, while one dominant tone per region reads as a thing that happened. Each biome
 * keeps its own secondary materials so a swamp still differs from a taiga, but the ash carpet on
 * top
 * is shared by the whole world — that shared layer is what makes it one event rather than many
 * edits.
 *
 * @param ashCarpet the thin covering laid over almost every surface
 * @param ashGround the dominant pale ground under the carpet
 * @param deepAsh the paler ground used where ash has piled up, and the ground a column falls back
 *     to when it has to hold something up — so this one must be a full block, or snow layers,
 *     carpets, and surviving plants placed on it will pop off at the next block update
 * @param scouredRock what an exposed, wind-stripped face becomes
 * @param grit coarser material mixed into scoured ground
 * @param dryBed what a drained watercourse is left as
 * @param charredWood the standing burnt trunk material
 * @param fallenWood the material for trunks lying on the ground
 * @param litter what remains of dead undergrowth
 * @param debris materials thrown out of craters
 */
public record AshPalette(
    Material ashCarpet,
    Material ashGround,
    Material deepAsh,
    Material scouredRock,
    Material grit,
    Material dryBed,
    Material charredWood,
    Material fallenWood,
    Material litter,
    List<Material> debris
) {

  /**
   * Normalises the debris list so a palette cannot be mutated after construction.
   */
  public AshPalette {
    debris = List.copyOf(debris);
  }

  /**
   * Returns a debris material by index, wrapping around.
   *
   * @param index any index
   * @return the material
   */
  public Material debrisAt(int index) {
    return debris.get(Math.abs(index) % debris.size());
  }
}
