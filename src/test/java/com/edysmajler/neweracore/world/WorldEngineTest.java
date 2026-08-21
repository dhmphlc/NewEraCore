package com.edysmajler.neweracore.world;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edysmajler.neweracore.config.HugeCraterConfig;
import com.edysmajler.neweracore.config.LevelsConfig;
import com.edysmajler.neweracore.config.NoiseConfig;
import com.edysmajler.neweracore.config.OreConfig;
import com.edysmajler.neweracore.config.PlanConfig;
import com.edysmajler.neweracore.config.StructuresConfig;
import com.edysmajler.neweracore.config.ThresholdConfig;
import com.edysmajler.neweracore.config.TownsConfig;
import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.plan.PlannedPlacer;
import com.edysmajler.neweracore.world.plan.WorldPlanBook;
import com.edysmajler.neweracore.world.structures.StructureManager;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import com.edysmajler.neweracore.world.towns.PlacedMarker;
import com.edysmajler.neweracore.world.towns.TownPlacer;
import java.nio.file.Path;
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

    withStubbedGround(
        new WorldEngine(config, marker, List.of(failing, second), emptyRegistry(), noPlans(),
            noPlacer(), Logger.getAnonymousLogger()))
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
    return withStubbedGround(new WorldEngine(
        engineConfig(enabled),
        marker,
        List.of(processor),
        emptyRegistry(),
        noPlans(),
        noPlacer(),
        Logger.getAnonymousLogger()
    ));
  }

  /**
   * Replaces the land lookup, which would otherwise ask a mocked world for a biome it cannot make.
   */
  private static WorldEngine withStubbedGround(WorldEngine engine) {
    WorldEngine spied = spy(engine);
    doReturn(LandLookup.EVERYWHERE).when(spied).land(any());
    return spied;
  }

  private static WorldEngineConfig engineConfig(boolean enabled) {
    WorldEngineConfig config = mock(WorldEngineConfig.class);
    when(config.isEnabled()).thenReturn(enabled);
    when(config.getScanDepth()).thenReturn(8);
    when(config.getNoise()).thenReturn(new NoiseConfig());
    when(config.getThresholds()).thenReturn(new ThresholdConfig());
    when(config.getLevels()).thenReturn(new LevelsConfig());
    when(config.getStructures()).thenReturn(new StructuresConfig());
    when(config.getHugeCraters()).thenReturn(new HugeCraterConfig());
    when(config.getTowns()).thenReturn(new TownsConfig());
    when(config.getOres()).thenReturn(new OreConfig());
    return config;
  }

  private static StructureManager emptyRegistry() {
    return new StructureManager(List.of());
  }

  /** A placer over the same nothing, so the engine has a collaborator but no plan to apply. */
  private static PlannedPlacer noPlacer() {
    return new PlannedPlacer(
        noPlans(),
        new PlanConfig(),
        new StructureManager(List.of()),
        new TownPlacer(mock(PlacedMarker.class), null, Logger.getAnonymousLogger()),
        mock(PlacedMarker.class),
        Logger.getAnonymousLogger());
  }

  /** A book over a folder with no plans in it, which is every world these tests care about. */
  private static WorldPlanBook noPlans() {
    return new WorldPlanBook(
        Path.of("no-such-folder"), new PlanConfig(), Logger.getAnonymousLogger());
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
