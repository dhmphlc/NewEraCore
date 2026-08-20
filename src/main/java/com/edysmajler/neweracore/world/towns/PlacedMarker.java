package com.edysmajler.neweracore.world.towns;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Records that a town has been built, so it builds exactly once.
 *
 * <p>The same contract as {@code StructureMarker}: the mark lives in the persistent data container
 * of the chunk holding the placement's centre, written <em>before</em> building, so two footprint
 * chunks arriving in the same tick cannot both build and a failing build cannot leave the site
 * eligible for a second, compounding attempt. It travels in the region file, so the guarantee
 * survives restarts for free.
 *
 * <p>The key carries the placement's own coordinates rather than being one fixed name per chunk,
 * so any future placement kind that packs several sites into one chunk can reuse it unchanged.
 */
public final class PlacedMarker {

  private final Plugin plugin;
  private final String prefix;

  /**
   * Creates a marker for one kind of placement.
   *
   * @param plugin the owning plugin, for its namespace
   * @param prefix the key prefix naming the placement kind, like {@code town} or {@code car}
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The plugin is a live handle kept only to namespace keys; it has no copy."
  )
  public PlacedMarker(Plugin plugin, String prefix) {
    this.plugin = plugin;
    this.prefix = prefix;
  }

  /**
   * Returns whether a placement has already been built.
   *
   * @param world the world the placement is in
   * @param blockX absolute block x of the placement centre
   * @param blockZ absolute block z of the placement centre
   * @return true when it is already there
   */
  public boolean isPlaced(World world, int blockX, int blockZ) {
    return world.getChunkAt(blockX >> 4, blockZ >> 4).getPersistentDataContainer()
        .has(keyFor(blockX, blockZ), PersistentDataType.BYTE);
  }

  /**
   * Records that a placement has been built.
   *
   * @param world the world the placement is in
   * @param blockX absolute block x of the placement centre
   * @param blockZ absolute block z of the placement centre
   */
  public void markPlaced(World world, int blockX, int blockZ) {
    world.getChunkAt(blockX >> 4, blockZ >> 4).getPersistentDataContainer()
        .set(keyFor(blockX, blockZ), PersistentDataType.BYTE, (byte) 1);
  }

  private NamespacedKey keyFor(int blockX, int blockZ) {
    return new NamespacedKey(plugin, prefix + "." + blockX + "." + blockZ);
  }
}
