package com.edysmajler.neweracore.command;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edysmajler.neweracore.config.HugeCraterConfig;
import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.feature.CraterSite;
import com.edysmajler.neweracore.world.feature.CraterSites;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import dev.jorel.commandapi.executors.CommandArguments;
import java.util.List;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class FindCratersTest {

  @Test
  void listsTheNearestCratersWithSomethingToWalkTowards() {
    PluginConfig config = Fixtures.config();
    WorldEngine engine = Fixtures.engine(config, true);
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 0, 0);

    List<CraterSite> expected = CraterSites.around(
        config.getWorldEngine().getHugeCraters(),
        engine.history(world),
        LandLookup.EVERYWHERE,
        Fixtures.SEED,
        0,
        0,
        3000
    );
    assertTrue(!expected.isEmpty(), "the test seed has no craters to list within 3000 blocks");

    new FindCraters(config, engine).run(player, mock(CommandArguments.class));

    String sent = Fixtures.messagesTo(player);
    CraterSite nearest = expected.get(0);

    assertTrue(sent.contains("[Core] "), sent);
    assertTrue(sent.contains(nearest.centerX() + ", " + nearest.centerZ()), sent);
    // Width, not radius: a radius means nothing to someone standing next to the hole
    assertTrue(sent.contains(String.valueOf(nearest.radius() * 2)), sent);
    // A distance with no direction is not something you can act on
    assertTrue(
        sent.contains("north") || sent.contains("south")
            || sent.contains("east") || sent.contains("west"),
        sent
    );
  }

  @Test
  void saysSoWhenThereAreNoneRatherThanNothing() {
    HugeCraterConfig none = mock(HugeCraterConfig.class);
    when(none.getSpacing()).thenReturn(768);
    when(none.getChance()).thenReturn(0.0);
    when(none.getRadiusMin()).thenReturn(18);
    when(none.getRadiusMax()).thenReturn(36);

    PluginConfig config = Fixtures.config(none);
    WorldEngine engine = Fixtures.engine(config, true);
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 0, 0);

    new FindCraters(config, engine).run(player, mock(CommandArguments.class));

    // Silence would read as a broken command rather than as an answer
    assertTrue(Fixtures.messagesTo(player).contains("No huge craters within"),
        Fixtures.messagesTo(player));
  }
}
