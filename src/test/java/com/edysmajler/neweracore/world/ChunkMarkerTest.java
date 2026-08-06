package com.edysmajler.neweracore.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class ChunkMarkerTest {

  @Test
  void untouchedChunkIsNotTransformed() {
    PersistentDataContainer container = mock(PersistentDataContainer.class);
    when(container.get(key(), PersistentDataType.INTEGER)).thenReturn(null);

    assertFalse(new ChunkMarker(plugin()).isTransformed(chunk(container)));
  }

  @Test
  void currentVersionCountsAsTransformed() {
    PersistentDataContainer container = mock(PersistentDataContainer.class);
    when(container.get(key(), PersistentDataType.INTEGER))
        .thenReturn(ChunkMarker.ENGINE_VERSION);

    assertTrue(new ChunkMarker(plugin()).isTransformed(chunk(container)));
  }

  @Test
  void olderVersionIsEligibleAgain() {
    PersistentDataContainer container = mock(PersistentDataContainer.class);
    when(container.get(key(), PersistentDataType.INTEGER))
        .thenReturn(ChunkMarker.ENGINE_VERSION - 1);

    // Raising ENGINE_VERSION is how a future phase reprocesses older chunks on purpose
    assertFalse(new ChunkMarker(plugin()).isTransformed(chunk(container)));
  }

  @Test
  void markingStoresCurrentVersion() {
    PersistentDataContainer container = mock(PersistentDataContainer.class);

    new ChunkMarker(plugin()).markTransformed(chunk(container));

    verify(container).set(key(), PersistentDataType.INTEGER, ChunkMarker.ENGINE_VERSION);
  }

  private static NamespacedKey key() {
    return new NamespacedKey("neweracore", "world-engine-version");
  }

  private static Plugin plugin() {
    Plugin plugin = mock(Plugin.class);
    when(plugin.getName()).thenReturn("NewEraCore");
    // NamespacedKey(Plugin, String) reads the namespace straight off the plugin
    when(plugin.namespace()).thenReturn("neweracore");
    return plugin;
  }

  private static Chunk chunk(PersistentDataContainer container) {
    Chunk chunk = mock(Chunk.class);
    when(chunk.getPersistentDataContainer()).thenReturn(container);
    return chunk;
  }
}
