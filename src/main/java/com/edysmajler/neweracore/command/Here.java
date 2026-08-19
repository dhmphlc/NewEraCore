package com.edysmajler.neweracore.command;

import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import com.edysmajler.neweracore.world.corruption.CorruptionZone;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.PlayerCommandExecutor;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Reports what the engine thinks of the ground the player is standing on.
 *
 * <p>Walking a world and guessing which zone you are in is how tuning goes wrong: ash that looks
 * too heavy might be the devastated band behaving correctly, and a stretch of untouched grass is
 * almost always a chunk that generated before the plugin was installed rather than a bug. So this
 * prints the answer — the level, and the numbers the passes actually ran on.
 *
 * <p>It reports the blended profile rather than the level's config defaults, because that is what
 * the world was built from: a chunk near a band boundary runs on numbers between two levels.
 */
public class Here implements PlayerCommandExecutor {

  private final PluginConfig config;
  private final WorldEngine engine;

  /**
   * Creates the executor.
   *
   * @param config the loaded plugin configuration
   * @param engine the running world engine, which owns each world's noise fields
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
    int chunkX = blockX >> 4;
    int chunkZ = blockZ >> 4;

    WorldEngineConfig engineConfig = config.getWorldEngine();
    CorruptionZone zone = CorruptionZone.resolve(
        engine.fields(player.getWorld()),
        engineConfig.getThresholds(),
        engineConfig.getLevels(),
        chunkX,
        chunkZ
    );
    CorruptionProfile profile = zone.profile();

    player.sendRichMessage(
        config.getMessagePrefix()
            + text("<gray>Zone at <white>%d, %d</white> in chunk <white>%d, %d</white>",
            blockX, blockZ, chunkX, chunkZ)
    );

    line(player, text("Level      <white>%s</white> at intensity <white>%.2f",
        zone.level(), zone.intensity()));
    line(player, text("Ash        carpet <white>%.2f</white>  deep <white>%.2f</white>  "
        + "drift <white>%.2f", profile.ashCarpetCoverage(), profile.deepAshShare(),
        profile.driftChance()));
    line(player, text("Trees      groves <white>%.2f</white>  snapped <white>%.2f</white>  "
        + "collapsed <white>%.2f", profile.livingGroveThreshold(), profile.snapShare(),
        profile.collapseShare()));
    line(player, text("Impacts    zone above <white>%.2f</white>  per chunk <white>%.2f</white>  "
        + "large <white>%.2f", profile.impactZoneThreshold(), profile.cratersPerZone(),
        profile.largeCraterShare()));

    sendState(player, location);
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
