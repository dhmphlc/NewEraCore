package com.edysmajler.neweracore.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.edysmajler.neweracore.config.HugeCraterConfig;
import com.edysmajler.neweracore.config.LevelsConfig;
import com.edysmajler.neweracore.config.NoiseConfig;
import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.config.StructuresConfig;
import com.edysmajler.neweracore.config.ThresholdConfig;
import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.ChunkMarker;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.structures.FighterJet;
import com.edysmajler.neweracore.world.structures.StructureManager;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.mockito.ArgumentCaptor;

/**
 * Shared mocks for the command tests.
 *
 * <p>The commands read real noise fields and a real structure registry over mocked Bukkit objects,
 * so what is under test is the actual answer for a seed rather than a stubbed one. Only the world,
 * the player, and the chunk mark are pretended.
 */
final class Fixtures {

  static final long SEED = 20260806L;

  private Fixtures() {}

  /**
   * Returns a player standing at a position in a world with a fixed seed.
   *
   * @param world the world to stand in
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the player
   */
  static Player playerAt(World world, int blockX, int blockZ) {
    Player player = mock(Player.class);
    when(player.getWorld()).thenReturn(world);
    when(player.getLocation()).thenReturn(new Location(world, blockX, 64, blockZ));
    return player;
  }

  /**
   * Returns a mocked world whose chunks all report as loaded and transformed.
   *
   * @return the world
   */
  static World world() {
    World world = mock(World.class);
    when(world.getSeed()).thenReturn(SEED);
    when(world.getUID()).thenReturn(UUID.randomUUID());
    when(world.getChunkAt(any(Location.class))).thenReturn(mock(Chunk.class));
    when(world.getSeaLevel()).thenReturn(63);
    return world;
  }

  /**
   * Returns a plugin config with real engine defaults and a plain message prefix.
   *
   * @return the config
   */
  static PluginConfig config() {
    return config(new HugeCraterConfig());
  }

  /**
   * Returns a plugin config with the given huge crater rules.
   *
   * @param craters the huge crater settings to use
   * @return the config
   */
  static PluginConfig config(HugeCraterConfig craters) {
    WorldEngineConfig engine = mock(WorldEngineConfig.class);
    when(engine.isEnabled()).thenReturn(true);
    when(engine.getScanDepth()).thenReturn(24);
    when(engine.getNoise()).thenReturn(new NoiseConfig());
    when(engine.getThresholds()).thenReturn(new ThresholdConfig());
    when(engine.getLevels()).thenReturn(new LevelsConfig());
    when(engine.getStructures()).thenReturn(new StructuresConfig());
    when(engine.getHugeCraters()).thenReturn(craters);

    PluginConfig config = mock(PluginConfig.class);
    when(config.getMessagePrefix()).thenReturn("[Core] ");
    when(config.getWorldEngine()).thenReturn(engine);
    return config;
  }

  /**
   * Returns an engine over the given config, with the chunk mark and the ground both stubbed.
   *
   * <p>The land lookup has to be replaced rather than fed: the real one asks the world generator
   * for a biome, and {@code Biome} cannot be produced or even mocked outside a running server. What
   * the lookup itself decides is covered where it is decided — {@code CraterSitesTest} and {@code
   * StructureSitesTest} — with no Bukkit involved at all.
   *
   * @param config the plugin config to read
   * @param transformed what the chunk mark should report
   * @return the engine
   */
  static WorldEngine engine(PluginConfig config, boolean transformed) {
    ChunkMarker marker = mock(ChunkMarker.class);
    when(marker.isTransformed(any())).thenReturn(transformed);

    WorldEngine engine = spy(new WorldEngine(
        config.getWorldEngine(),
        marker,
        List.of(),
        new StructureManager(List.of(new FighterJet())),
        Logger.getAnonymousLogger()
    ));

    doReturn(LandLookup.EVERYWHERE).when(engine).land(any());
    return engine;
  }

  /**
   * Returns everything a player was sent, joined into one string for content assertions.
   *
   * @param player the player to read
   * @return the messages
   */
  static String messagesTo(Player player) {
    ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(player, org.mockito.Mockito.atLeastOnce())
        .sendRichMessage(sent.capture());
    return String.join("\n", sent.getAllValues());
  }
}
