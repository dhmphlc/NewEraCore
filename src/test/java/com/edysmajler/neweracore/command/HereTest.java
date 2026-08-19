package com.edysmajler.neweracore.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.corruption.CorruptionZone;
import dev.jorel.commandapi.executors.CommandArguments;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class HereTest {

  private final PluginConfig config = Fixtures.config();
  private final WorldEngine engine = Fixtures.engine(config, true);

  @Test
  void reportsTheZoneUnderThePlayer() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 1234, -5678);

    new Here(config, engine).run(player, mock(CommandArguments.class));

    String sent = Fixtures.messagesTo(player);
    final CorruptionZone expected = zoneAt(world, 1234 >> 4, -5678 >> 4);

    assertTrue(sent.contains("[Core] "), sent);
    assertTrue(sent.contains("1234, -5678"), sent);
    // Chunk 77, -355 — the readout has to agree with where the engine thinks the player is
    assertTrue(sent.contains("77, -355"), sent);
    assertTrue(sent.contains(expected.level().name()), sent);
  }

  @Test
  void reportsTheNumbersThePassesActuallyRanOn() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 512, 512);

    new Here(config, engine).run(player, mock(CommandArguments.class));

    String sent = Fixtures.messagesTo(player);
    CorruptionZone zone = zoneAt(world, 512 >> 4, 512 >> 4);

    // The blended profile, not the level's configured defaults: a chunk near a band boundary runs
    // on numbers between two levels, and printing the defaults would hide exactly that.
    assertTrue(
        sent.contains(String.format(Locale.ROOT, "%.2f", zone.profile().ashCarpetCoverage())),
        sent
    );
    assertTrue(
        sent.contains(String.format(Locale.ROOT, "%.2f", zone.intensity())),
        sent
    );
  }

  @Test
  void usesDecimalPointsWhateverTheServerLocale() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 64, 64);

    new Here(config, engine).run(player, mock(CommandArguments.class));

    // A Slovenian or German default locale formats 0.72 as "0,72", which would make every number in
    // the readout disagree with the config file it is meant to be compared against. Coordinates are
    // separated by a comma and a space, so only a comma pressed between two digits is a decimal
    // one.
    String sent = Fixtures.messagesTo(player);
    assertFalse(
        Pattern.compile("\\d,\\d").matcher(sent).find(),
        "decimal comma in the readout: " + sent
    );
  }

  @Test
  void warnsWhenTheChunkPredatesThePlugin() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 0, 0);

    new Here(config, Fixtures.engine(config, false)).run(player, mock(CommandArguments.class));

    // The single most common "the engine is broken" report there is
    assertTrue(Fixtures.messagesTo(player).contains("generated before the plugin"),
        Fixtures.messagesTo(player));
  }

  @Test
  void staysQuietWhenTheChunkWasTransformed() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 0, 0);

    new Here(config, engine).run(player, mock(CommandArguments.class));

    assertFalse(Fixtures.messagesTo(player).contains("generated before the plugin"));
  }

  private CorruptionZone zoneAt(World world, int chunkX, int chunkZ) {
    return CorruptionZone.resolve(
        engine.fields(world),
        config.getWorldEngine().getThresholds(),
        config.getWorldEngine().getLevels(),
        chunkX,
        chunkZ
    );
  }
}
