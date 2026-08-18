package com.edysmajler.neweracore.command;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.history.Landmark;
import com.edysmajler.neweracore.world.history.LandmarkType;
import dev.jorel.commandapi.executors.CommandArguments;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class LocateTest {

  private final PluginConfig config = Fixtures.config();
  private final WorldEngine engine = Fixtures.engine(config, true);

  @Test
  void findsTheNearestPlaceOfThatKind() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 0, 0);

    run(player, "airport");

    String sent = Fixtures.messagesTo(player);
    Landmark expected = nearest(world, LandmarkType.AIRPORT);

    assertTrue(sent.contains("AIRPORT"), sent);
    assertTrue(sent.contains(expected.centerX() + ", " + expected.centerZ()),
        "reported something other than the nearest airport: " + sent);
  }

  @Test
  void searchingOutwardStillReturnsTheNearest() {
    World world = Fixtures.world();

    // A site in the corner of one ring can be further off than one just inside the next ring, so
    // stopping at the first ring with a hit would report the wrong place. Checked from several
    // starting points, since the failure only shows when the nearest sits diagonally.
    for (int[] from : new int[][] {{0, 0}, {700, -700}, {-2600, 1400}, {4300, 4300}}) {
      Player player = Fixtures.playerAt(world, from[0], from[1]);
      run(player, "radio_tower");

      Landmark expected = nearest(world, LandmarkType.RADIO_TOWER, from[0], from[1]);
      assertTrue(
          Fixtures.messagesTo(player).contains(expected.centerX() + ", " + expected.centerZ()),
          "from " + from[0] + ", " + from[1] + " the nearest radio tower was missed"
      );
    }
  }

  @Test
  void anAirportAlsoReportsItsRunway() {
    World world = Fixtures.world();
    Player player = Fixtures.playerAt(world, 0, 0);

    run(player, "airport");

    // The one landmark with something actually built on it, so the readout can say what is there
    String sent = Fixtures.messagesTo(player);
    assertTrue(sent.contains("runway"), sent);
    assertTrue(sent.contains("bearing"), sent);
  }

  @Test
  void everyKindCanBeAskedFor() {
    for (LandmarkType type : LandmarkType.values()) {
      assertTrue(
          List.of(Locate.targets()).contains(type.name().toLowerCase(Locale.ROOT)),
          type + " cannot be located because it is not offered as an argument"
      );
    }
  }

  private void run(Player player, String target) {
    CommandArguments args = mock(CommandArguments.class);
    when(args.get(Locate.TARGET)).thenReturn(target);

    new Locate(config, engine).run(player, args);
  }

  private Landmark nearest(World world, LandmarkType type) {
    return nearest(world, type, 0, 0);
  }

  /**
   * Finds the nearest site of a kind by brute force, which is what the command has to agree with.
   */
  private Landmark nearest(World world, LandmarkType type, int blockX, int blockZ) {
    int spacing = config.getWorldEngine().getHistory().getLandmarks().getSpacing();
    int cellX = Math.floorDiv(blockX, spacing);
    int cellZ = Math.floorDiv(blockZ, spacing);

    List<Landmark> matches = new ArrayList<>();
    for (int dx = -12; dx <= 12; dx++) {
      for (int dz = -12; dz <= 12; dz++) {
        engine.history(world).landmarks().siteIn(cellX + dx, cellZ + dz)
            .filter(site -> site.type() == type)
            .ifPresent(matches::add);
      }
    }

    assertTrue(!matches.isEmpty(), "the test seed has no " + type + " to find");
    return matches.stream()
        .min((left, right) -> Double.compare(
            left.distanceTo(blockX, blockZ), right.distanceTo(blockX, blockZ)))
        .orElseThrow();
  }
}
