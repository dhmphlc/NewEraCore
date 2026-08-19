package com.edysmajler.neweracore.command;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.structures.StructureSite;
import com.edysmajler.neweracore.world.structures.StructureSites;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import dev.jorel.commandapi.executors.CommandArguments;
import java.util.List;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class LocateTest {

  private final PluginConfig config = Fixtures.config();
  private final WorldEngine engine = Fixtures.engine(config, true);

  @Test
  void findsTheNearestSiteOfThatKind() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 0, 0);

    run(player, "fighter_jet");

    String sent = Fixtures.messagesTo(player);
    StructureSite expected = nearest(world, 0, 0);

    assertTrue(sent.contains("fighter_jet"), sent);
    assertTrue(sent.contains(expected.centerX() + ", " + expected.centerZ()),
        "reported something other than the nearest site: " + sent);
  }

  @Test
  void reportsBearingTowardTheSite() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, -3000, 2400);

    run(player, "fighter_jet");

    // A distance with no bearing is not something you can act on
    String sent = Fixtures.messagesTo(player);
    assertTrue(
        sent.contains("north") || sent.contains("south")
            || sent.contains("east") || sent.contains("west"),
        sent
    );
  }

  @Test
  void anUnknownKindListsWhatExists() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 0, 0);

    run(player, "battleship");

    String sent = Fixtures.messagesTo(player);
    assertTrue(sent.contains("Nothing of that kind exists"), sent);
    // The registry's own ids, so the answer stays honest as structures are added
    assertTrue(sent.contains("fighter_jet"), sent);
  }

  private void run(Player player, String target) {
    CommandArguments args = mock(CommandArguments.class);
    when(args.get(Locate.TARGET)).thenReturn(target);

    new Locate(config, engine).run(player, args);
  }

  /**
   * Finds the nearest site by brute force, which is what the command has to agree with.
   */
  private StructureSite nearest(World world, int blockX, int blockZ) {
    List<StructureSite> sites = StructureSites.around(
        config.getWorldEngine().getStructures(),
        engine.structures(),
        LandLookup.EVERYWHERE,
        Fixtures.SEED,
        blockX,
        blockZ,
        6000
    );

    assertTrue(!sites.isEmpty(), "the test seed has no sites to find within 6000 blocks");
    return sites.get(0);
  }
}
