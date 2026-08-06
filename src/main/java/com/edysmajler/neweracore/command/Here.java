package com.edysmajler.neweracore.command;

import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import com.edysmajler.neweracore.world.history.Landmark;
import com.edysmajler.neweracore.world.history.RegionProfile;
import com.edysmajler.neweracore.world.infrastructure.Route;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.PlayerCommandExecutor;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Reports what the engine thinks happened where the player is standing.
 *
 * <p>Everything this engine does follows from one question, and until now there was no way to ask
 * it in game. Walking a world and guessing which region you are in is how tuning goes wrong: an
 * ashfall that looks too heavy might be a front line behaving correctly, and a stretch of untouched
 * grass is almost always a chunk that generated before the plugin was installed rather than a bug.
 * So this prints the answer — the story, the three history layers, and the numbers the passes
 * actually ran on.
 *
 * <p>It reports the shaped profile rather than the level's defaults, because that is what the world
 * was built from. A number here that does not match config.yml is the history doing its job.
 */
public class Here implements PlayerCommandExecutor {

  private final PluginConfig config;
  private final WorldEngine engine;

  /**
   * Creates the executor.
   *
   * @param config the loaded plugin configuration
   * @param engine the running world engine, which owns each world's history
   */
  public Here(PluginConfig config, WorldEngine engine) {
    this.config = config;
    this.engine = engine;
  }

  @Override
  public void run(Player player, CommandArguments args) {
    Location location = player.getLocation();
    int blockX = location.getBlockX();
    int blockZ = location.getBlockZ();

    RegionProfile region = engine.history(player.getWorld()).at(blockX, blockZ);
    final CorruptionProfile profile = region.profile();

    player.sendRichMessage(
        config.getMessagePrefix()
            + text("<gray>Region at <white>%d, %d</white> in chunk <white>%d, %d</white>",
            blockX, blockZ, blockX >> 4, blockZ >> 4)
    );

    line(player, text("Story      <aqua>%s</aqua> <dark_gray>%s",
        region.story(), region.story().summary()));
    line(player, text("Level      <white>%s</white> at intensity <white>%.2f",
        region.corruptionLevel(), region.corruptionIntensity()));
    line(player, text("History    war <white>%.2f</white>  ashfall <white>%.2f</white>  "
        + "restoration <white>%.2f", region.warIntensity(), region.ashfall(),
        region.restoration()));
    line(player, text("Ash        carpet <white>%.2f</white>  deep <white>%.2f</white>  "
        + "drift <white>%.2f", profile.ashCarpetCoverage(), profile.deepAshShare(),
        profile.driftChance()));
    line(player, text("Trees      groves <white>%.2f</white>  snapped <white>%.2f</white>  "
        + "collapsed <white>%.2f", profile.livingGroveThreshold(), profile.snapShare(),
        profile.collapseShare()));
    line(player, text("Impacts    zone above <white>%.2f</white>  per chunk <white>%.2f</white>  "
        + "large <white>%.2f", profile.impactZoneThreshold(), profile.cratersPerZone(),
        profile.largeCraterShare()));

    sendLandmarks(player, region, blockX, blockZ);
    sendRoutes(player, blockX, blockZ);
    sendState(player, location);
  }

  private void sendLandmarks(Player player, RegionProfile region, int blockX, int blockZ) {
    if (region.onLandmark()) {
      Landmark mark = region.landmark().orElseThrow();
      line(player, text("Landmark   standing on <aqua>%s</aqua> <dark_gray>(radius %d)",
          mark.type(), mark.radius()));
      return;
    }

    region.nearestLandmark().ifPresent(mark -> line(player, text(
        "Landmark   <aqua>%s</aqua> <gray>at <white>%d, %d</white>, %d blocks %s",
        mark.type(), mark.centerX(), mark.centerZ(),
        Math.round(mark.distanceTo(blockX, blockZ)),
        Bearing.of(mark.centerX() - (double) blockX, mark.centerZ() - (double) blockZ)
    )));
  }

  /**
   * Reports what runs past here, which is what anything built later has to answer to.
   */
  private void sendRoutes(Player player, int blockX, int blockZ) {
    engine.infrastructure(player.getWorld()).nearestRoute(blockX, blockZ).ifPresent(route -> {
      int distance = (int) Math.round(route.distanceTo(blockX, blockZ));
      String where = route.covers(blockX, blockZ)
          ? "you are standing on it"
          : distance + " blocks away";

      line(player, text("Route      <aqua>%s</aqua> <gray>%s to %s, %s",
          route.type().label().toUpperCase(Locale.ROOT), route.from().type(), route.to().type(),
          where));
    });
  }

  /**
   * Warns about the two things that make a correct engine look broken.
   */
  private void sendState(Player player, Location location) {
    if (!config.getWorldEngine().isEnabled()) {
      line(player, "<yellow>The engine is disabled, so nothing here was built from the above.");
      return;
    }

    if (!engine.hasTransformed(location.getChunk())) {
      line(player, "<yellow>This chunk carries no engine mark: it generated before the plugin, so "
          + "it is untouched vanilla ground.");
    }
  }

  private static void line(Player player, String message) {
    player.sendRichMessage("<gray>" + message);
  }

  private static String text(String format, Object... values) {
    // Root locale: a comma for the decimal point would make these unreadable beside the config
    return String.format(Locale.ROOT, format, values);
  }
}
