package com.edysmajler.neweracore.world.structures;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Records that a site's structure has been built, so it is built exactly once.
 *
 * <p>The trigger for a placement is "the last chunk of the footprint generated" — but chunk
 * generation is asynchronous, so two footprint chunks can arrive in the same tick and both observe
 * a complete footprint. The mark settles it: whoever checks first places, and the mark is what the
 * second one sees.
 *
 * <p>It lives in the persistent data container of the chunk holding the site's <em>centre</em>,
 * which travels in the region file like the engine version mark does, so the guarantee survives
 * restarts for free. Sites wander only inside their own grid cell and a cell is far wider than a
 * chunk, so no two sites can ever contend for the same centre chunk.
 */
public class StructureMarker {

  private final NamespacedKey key;

  /**
   * Creates a marker bound to the given plugin's namespace.
   *
   * @param plugin the owning plugin
   */
  public StructureMarker(Plugin plugin) {
    this.key = new NamespacedKey(plugin, "structure-placed");
  }

  /**
   * Returns whether a site's structure has already been built.
   *
   * <p>Loads the centre chunk when it is not already loaded, which is cheap here: the caller only
   * asks once the whole footprint is generated, so the load never generates terrain.
   *
   * @param world the world the site is in
   * @param site the site to check
   * @return true when the structure is already there
   */
  public boolean isPlaced(World world, StructureSite site) {
    return centerChunk(world, site).getPersistentDataContainer()
        .has(key, PersistentDataType.BYTE);
  }

  /**
   * Records that a site's structure has been built.
   *
   * @param world the world the site is in
   * @param site the placed site
   */
  public void markPlaced(World world, StructureSite site) {
    centerChunk(world, site).getPersistentDataContainer()
        .set(key, PersistentDataType.BYTE, (byte) 1);
  }

  private Chunk centerChunk(World world, StructureSite site) {
    return world.getChunkAt(site.centerX() >> 4, site.centerZ() >> 4);
  }
}
