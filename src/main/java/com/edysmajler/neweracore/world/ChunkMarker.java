package com.edysmajler.neweracore.world;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Tracks which chunks the world engine has already transformed.
 *
 * <p>The marker is stored in the chunk's persistent data container, which lives in the region file
 * alongside the chunk itself. That makes the "transform once" guarantee survive restarts without a
 * database table that grows with every explored chunk.
 *
 * <p>The stored value is the engine version that processed the chunk, not a plain flag. A future
 * phase can raise {@link #ENGINE_VERSION} to deliberately reprocess older chunks, while chunks
 * already at the current version are always skipped.
 */
public class ChunkMarker {

  /** Version stamped into every transformed chunk. */
  public static final int ENGINE_VERSION = 1;

  private final NamespacedKey key;

  /**
   * Creates a marker bound to the given plugin's namespace.
   *
   * @param plugin the owning plugin
   */
  public ChunkMarker(Plugin plugin) {
    this.key = new NamespacedKey(plugin, "world-engine-version");
  }

  /**
   * Returns whether the chunk has already been transformed by this engine version.
   *
   * @param chunk the chunk to check
   * @return true if the chunk needs no further transformation
   */
  public boolean isTransformed(Chunk chunk) {
    Integer version = chunk.getPersistentDataContainer()
        .get(key, PersistentDataType.INTEGER);
    return version != null && version >= ENGINE_VERSION;
  }

  /**
   * Records that the chunk has been transformed by this engine version.
   *
   * @param chunk the chunk to mark
   */
  public void markTransformed(Chunk chunk) {
    PersistentDataContainer container = chunk.getPersistentDataContainer();
    container.set(key, PersistentDataType.INTEGER, ENGINE_VERSION);
  }
}
