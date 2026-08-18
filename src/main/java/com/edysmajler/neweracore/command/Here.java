package com.edysmajler.neweracore.command;

import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import com.edysmajler.neweracore.world.history.Landmark;
import com.edysmajler.neweracore.world.history.LandmarkType;
import com.edysmajler.neweracore.world.history.RegionProfile;
import com.edysmajler.neweracore.world.history.SiteGround;
import com.edysmajler.neweracore.world.history.TerrainQuery;
import com.edysmajler.neweracore.world.infrastructure.Route;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.PlayerCommandExecutor;
import java.util.ArrayList;
import java.util.List;
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
 *
 * <p>It also reports the <em>ground</em>, which is the half of siting that was previously
 * impossible to see. Whether a dam belongs somewhere is a number derived from the terrain, and one
 * you
 * cannot read is a number you cannot tune: the only way to check it was to walk to a landmark and
 * judge by eye whether it looked like it made sense. So the measurements are printed, and the
 * suitability beside them comes from {@link LandmarkType#suitability} rather than being recomputed
 * here — a readout with its own copy of the rule explains the wrong thing the moment the two drift.
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

    TerrainQuery terrain = engine.land(player.getWorld()).terrainQuery();

    sendTerrain(player, terrain.at(blockX, blockZ));
    sendLandmarks(player, region, terrain, blockX, blockZ);
    sendRoutes(player, blockX, blockZ);
    sendState(player, location);
  }

  /**
   * Reports what the generator puts on the ground here, and what could be built on it.
   *
   * <p>Printed compactly rather than one measurement per line: this command already runs to a dozen
   * lines, and a readout long enough to scroll its own top away is one nobody reads twice.
   */
  private void sendTerrain(Player player, SiteGround ground) {
    if (!ground.dryLand()) {
      line(player, "Terrain    <aqua>open water</aqua> <dark_gray>— nothing can be sited here");
      return;
    }

    line(player, text("Terrain    water <white>%.2f</white>  river <white>%.2f</white>  "
        + "relief <white>%.2f", ground.water(), ground.river(), ground.relief()));
    line(player, text("           enclosure <white>%.2f</white>  valley <white>%.2f</white>  "
        + "open <white>%.2f", ground.enclosure(), ground.valley(), ground.openness()));

    List<String> suits = new ArrayList<>();

    for (LandmarkType type : LandmarkType.values()) {
      // Only the types the ground can rule on. The rest score 1 anywhere dry, and a column of ones
      // teaches nothing.
      if (type.needsParticularGround()) {
        suits.add(text("%s <white>%.2f</white>",
            type.name().toLowerCase(Locale.ROOT), type.suitability(ground)));
      }
    }

    line(player, "Suits      " + String.join("  ", suits)
        + " <dark_gray>— everything else needs only dry land");
  }

  private void sendLandmarks(
      Player player,
      RegionProfile region,
      TerrainQuery terrain,
      int blockX,
      int blockZ
  ) {
    if (region.onLandmark()) {
      Landmark mark = region.landmark().orElseThrow();
      line(player, text("Landmark   standing on <aqua>%s</aqua> <dark_gray>(radius %d, suits its "
          + "ground %.2f)", mark.type(), mark.radius(), suits(mark, terrain)));
      return;
    }

    region.nearestLandmark().ifPresent(mark -> line(player, text(
        "Landmark   <aqua>%s</aqua> <gray>at <white>%d, %d</white>, %d blocks %s "
            + "<dark_gray>(suits its ground %.2f)",
        mark.type(), mark.centerX(), mark.centerZ(),
        Math.round(mark.distanceTo(blockX, blockZ)),
        Bearing.of(mark.centerX() - (double) blockX, mark.centerZ() - (double) blockZ),
        suits(mark, terrain)
    )));
  }

  /**
   * Returns how well a landmark suits the ground it actually stands on.
   *
   * <p>Measured at the site rather than where the player is standing, which is the whole use of it:
   * the question a landmark raises is never "is this ground good" but "did that thing over there
   * have any business being built where it is".
   */
  private static double suits(Landmark mark, TerrainQuery terrain) {
    return mark.type().suitability(terrain.at(mark.centerX(), mark.centerZ()));
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
