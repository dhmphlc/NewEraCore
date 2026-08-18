package com.edysmajler.neweracore.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.history.HistoryEngine;
import com.edysmajler.neweracore.world.history.LandmarkType;
import com.edysmajler.neweracore.world.history.RegionProfile;
import com.edysmajler.neweracore.world.history.SiteGround;
import com.edysmajler.neweracore.world.history.TerrainQuery;
import com.edysmajler.neweracore.world.terrain.LandLookup;
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
  void reportsTheRegionUnderThePlayer() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 1234, -5678);

    new Here(config, engine).run(player, mock(CommandArguments.class));

    String sent = Fixtures.messagesTo(player);
    final RegionProfile expected = engine.history(world).at(1234, -5678);

    assertTrue(sent.contains("[Core] "), sent);
    assertTrue(sent.contains("1234, -5678"), sent);
    // Chunk 77, -355 — the readout has to agree with where the engine thinks the player is
    assertTrue(sent.contains("77, -355"), sent);
    assertTrue(sent.contains(expected.story().name()), sent);
    assertTrue(sent.contains(expected.corruptionLevel().name()), sent);
  }

  @Test
  void reportsTheNumbersThePassesActuallyRanOn() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 512, 512);
    HistoryEngine history = engine.history(world);

    new Here(config, engine).run(player, mock(CommandArguments.class));

    String sent = Fixtures.messagesTo(player);
    RegionProfile region = history.at(512, 512);

    // The shaped profile, not the level's configured defaults: a number that disagrees with
    // config.yml is the history doing its job, and printing the defaults would hide exactly that.
    assertTrue(
        sent.contains(String.format(Locale.ROOT, "%.2f", region.profile().ashCarpetCoverage())),
        sent
    );
    assertTrue(
        sent.contains(String.format(Locale.ROOT, "%.2f", region.warIntensity())),
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

  @Test
  void reportsTheGroundUnderThePlayerNotJustTheHistory() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 800, -800);

    new Here(config, engine).run(player, mock(CommandArguments.class));

    String sent = Fixtures.messagesTo(player);

    // Which half of siting is being reported matters: the story says whether a place belongs in a
    // region, and this says whether it could have been built on the ground. Only one of them was
    // visible in game before, which is why a dam on dry land could ship unnoticed.
    assertTrue(sent.contains("Terrain"), sent);
    assertTrue(sent.contains("water"), sent);
    assertTrue(sent.contains("river"), sent);
    assertTrue(sent.contains("relief"), sent);
    assertTrue(sent.contains("enclosure"), sent);
    assertTrue(sent.contains("valley"), sent);
  }

  @Test
  void theSuitabilityShownIsTheOneSitingWouldUse() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 2048, 3072);
    WorldEngine local = Fixtures.engine(config, true);
    LandLookup ground = ridgedGround();
    doReturn(ground).when(local).land(any());

    new Here(config, local).run(player, mock(CommandArguments.class));

    String sent = Fixtures.messagesTo(player);
    SiteGround here = ground.terrainQuery().at(2048, 3072);

    // The point of the whole readout. If this ever has to be computed a second way to make the test
    // pass, the command has grown its own copy of the rule and will explain the wrong thing.
    for (LandmarkType type : LandmarkType.values()) {
      if (type.needsParticularGround()) {
        String shown = String.format(Locale.ROOT, "%.2f", type.suitability(here));
        assertTrue(sent.contains(shown), type + " scored " + shown + " but was not shown: " + sent);
      }
    }
  }

  @Test
  void groundThatCanHoldNothingSaysSoOutright() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 128, 128);
    WorldEngine local = Fixtures.engine(config, true);
    doReturn((LandLookup) (blockX, blockZ) -> false).when(local).land(any());

    new Here(config, local).run(player, mock(CommandArguments.class));

    // Five zeroes would be a true readout and a useless one. Standing in the sea is the single most
    // likely reason a landmark is missing from where somebody expected one.
    assertTrue(Fixtures.messagesTo(player).contains("open water"), Fixtures.messagesTo(player));
  }

  @Test
  void riverInValleyScoresHigherThanOneOnOpenGround() {
    SiteGround flat = riverGround(Integer.MAX_VALUE).terrainQuery().at(0, 0);
    SiteGround held = riverGround(TerrainQuery.FAR - 8).terrainQuery().at(0, 0);

    // The distinction the old three-boolean seam could not draw: both of these have a river well
    // within reach, and only one of them is a place you could dam.
    assertTrue(
        LandmarkType.HYDROELECTRIC_DAM.suitability(held)
            > LandmarkType.HYDROELECTRIC_DAM.suitability(flat),
        "a river valley scored no better than a river across open ground"
    );
  }

  /** Ground with a river around the site and high ground beyond the given radius. */
  private static LandLookup riverGround(int ruggedBeyond) {
    return new LandLookup() {
      @Override
      public boolean isLand(int blockX, int blockZ) {
        return true;
      }

      @Override
      public TerrainQuery.Ground ground(int blockX, int blockZ) {
        double distance = Math.sqrt(blockX * (double) blockX + blockZ * (double) blockZ);

        if (distance >= ruggedBeyond) {
          return TerrainQuery.Ground.RUGGED;
        }

        return distance == 0.0 ? TerrainQuery.Ground.OPEN : TerrainQuery.Ground.RIVER;
      }
    };
  }

  /** Ground broken enough that an airport should score badly on it. */
  private static LandLookup ridgedGround() {
    return riverGround(TerrainQuery.NEAR);
  }
}
