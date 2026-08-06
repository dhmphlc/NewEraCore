package com.edysmajler.neweracore.world.history;

/**
 * One large-scale layer of what happened to the world before the player arrived.
 *
 * <p>A history map answers a single question about a place — how hard the fighting was, how much
 * ash fell, how much life held on — as a value between 0 and 1. Nothing in the engine rolls dice
 * for these questions any more: a region's character has to be a property of the <em>world</em>, or
 * two systems asking "what happened here" would get two different answers and the place would
 * contradict itself.
 *
 * <p>Three rules hold for every implementation:
 *
 * <ol>
 * <li><strong>Deterministic.</strong> A pure function of the world seed and the coordinates. No
 * state, no order dependence, no cached mutation. The same spot answers the same way forever,
 * whether it is asked once at chunk generation or a thousand times by a later system.</li>
 * <li><strong>Percentile.</strong> The value is its own position in the map's distribution, so 0.6
 * genuinely means "the top 40% of the world". {@code NoiseField} handles this; a hand-rolled map
 * must not skip it, because an uncalibrated field clusters around 0.5 and every threshold above 0.8
 * silently becomes unreachable.</li>
 * <li><strong>Large.</strong> Features of 512 blocks and up. These layers decide what kind of place
 * this is, not what the next block looks like; that is what the fine fields are for.</li>
 * </ol>
 *
 * <p>To add a map: implement this, give it a salt no other field uses, add its scale to {@code
 * HistoryConfig}, hold it in {@link HistoryMaps}, and expose whatever it decides on {@link
 * RegionProfile}. Nothing else needs to know it exists.
 */
public interface HistoryMap {

  /**
   * Returns the short name of this map, used in debug output.
   *
   * @return the name
   */
  String name();

  /**
   * Returns this map's value at a world position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the value between 0 and 1
   */
  double at(int blockX, int blockZ);
}
