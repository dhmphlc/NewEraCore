package com.edysmajler.neweracore.world;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edysmajler.neweracore.config.HistoryConfig;
import com.edysmajler.neweracore.config.HugeCraterConfig;
import com.edysmajler.neweracore.config.InfrastructureConfig;
import com.edysmajler.neweracore.config.LevelsConfig;
import com.edysmajler.neweracore.config.NoiseConfig;
import com.edysmajler.neweracore.config.OreConfig;
import com.edysmajler.neweracore.config.ThresholdConfig;
import com.edysmajler.neweracore.config.WorldEngineConfig;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class WorldEngineTest {

  @Test
  void transformsNewChunkOnce() {
    ChunkProcessor processor = mock(ChunkProcessor.class);
    ChunkMarker marker = mock(ChunkMarker.class);
    ChunkLoadEvent event = event(true);

    engine(processor, marker, true).onChunkLoad(event);

    verify(processor, times(1)).process(any());
    verify(marker, times(1)).markTransformed(event.getChunk());
  }

  @Test
  void skipsChunksThatAreNotNew() {
    ChunkProcessor processor = mock(ChunkProcessor.class);
    ChunkMarker marker = mock(ChunkMarker.class);
    ChunkLoadEvent event = event(false);

    engine(processor, marker, true).onChunkLoad(event);

    verify(processor, never()).process(any());
    verify(marker, never()).markTransformed(event.getChunk());
  }

  @Test
  void skipsChunksAlreadyTransformed() {
    ChunkProcessor processor = mock(ChunkProcessor.class);
    ChunkMarker marker = mock(ChunkMarker.class);
    ChunkLoadEvent event = event(true);
    when(marker.isTransformed(event.getChunk())).thenReturn(true);

    engine(processor, marker, true).onChunkLoad(event);

    verify(processor, never()).process(any());
    verify(marker, never()).markTransformed(event.getChunk());
  }

  @Test
  void marksChunkBeforeRunningPipeline() {
    ChunkProcessor processor = mock(ChunkProcessor.class);
    ChunkMarker marker = mock(ChunkMarker.class);
    ChunkLoadEvent event = event(true);

    engine(processor, marker, true).onChunkLoad(event);

    // Marking first is what stops a processor failure from leaving the chunk eligible again
    InOrder order = inOrder(marker, processor);
    order.verify(marker).markTransformed(event.getChunk());
    order.verify(processor).process(any());
  }

  @Test
  void survivesProcessorFailure() {
    ChunkProcessor failing = mock(ChunkProcessor.class);
    when(failing.name()).thenReturn("boom");
    doThrow(new IllegalStateException("boom")).when(failing).process(any());
    ChunkProcessor second = mock(ChunkProcessor.class);
    ChunkMarker marker = mock(ChunkMarker.class);
    WorldEngineConfig config = engineConfig(true);

    new WorldEngine(config, marker, List.of(failing, second), Logger.getAnonymousLogger())
        .onChunkLoad(event(true));

    // A broken stage must not stop the stages after it
    verify(second, times(1)).process(any());
  }

  @Test
  void doesNothingWhenDisabled() {
    ChunkProcessor processor = mock(ChunkProcessor.class);
    ChunkMarker marker = mock(ChunkMarker.class);
    ChunkLoadEvent event = event(true);

    engine(processor, marker, false).onChunkLoad(event);

    verify(processor, never()).process(any());
    verify(marker, never()).isTransformed(event.getChunk());
  }

  private static WorldEngine engine(
      ChunkProcessor processor,
      ChunkMarker marker,
      boolean enabled
  ) {
    return new WorldEngine(
        engineConfig(enabled),
        marker,
        List.of(processor),
        Logger.getAnonymousLogger()
    );
  }

  private static WorldEngineConfig engineConfig(boolean enabled) {
    WorldEngineConfig config = mock(WorldEngineConfig.class);
    when(config.isEnabled()).thenReturn(enabled);
    when(config.getScanDepth()).thenReturn(8);
    when(config.getNoise()).thenReturn(new NoiseConfig());
    when(config.getThresholds()).thenReturn(new ThresholdConfig());
    when(config.getLevels()).thenReturn(new LevelsConfig());
    when(config.getHistory()).thenReturn(new HistoryConfig());
    when(config.getInfrastructure()).thenReturn(new InfrastructureConfig());
    when(config.getHugeCraters()).thenReturn(new HugeCraterConfig());
    when(config.getOres()).thenReturn(new OreConfig());
    return config;
  }

  private static ChunkLoadEvent event(boolean newChunk) {
    World world = mock(World.class);
    when(world.getMinHeight()).thenReturn(-64);
    when(world.getMaxHeight()).thenReturn(320);
    when(world.getSeed()).thenReturn(1234L);
    when(world.getUID()).thenReturn(UUID.randomUUID());

    ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
    when(snapshot.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(64);
    when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenReturn(Material.STONE);

    Chunk chunk = mock(Chunk.class);
    when(chunk.getWorld()).thenReturn(world);
    when(chunk.getChunkSnapshot(anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(snapshot);

    ChunkLoadEvent event = mock(ChunkLoadEvent.class);
    when(event.isNewChunk()).thenReturn(newChunk);
    when(event.getChunk()).thenReturn(chunk);
    return event;
  }
}
