package com.edysmajler.neweracore.command;

import com.edysmajler.neweracore.config.HugeCraterConfig;
import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.feature.CraterSite;
import com.edysmajler.neweracore.world.feature.CraterSites;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.PlayerCommandExecutor;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Lists the huge impact craters near the player.
 *
 * <p>Huge craters are the events the whole world is reacting to, and they are rare enough — one
 * every kilometre or two, only in scarred-or-worse country — that finding one by
 * walking is mostly luck. Since their positions are a pure function of the world seed, they can
 * simply be looked up, from anywhere, without loading a single chunk.
 *
 * <p>Reported distances are to the crater's centre and the sizes are what will be carved there. The
 * ground itself is only cut when a chunk generates, so a listed crater in terrain that already
 * exists will not be there — the coordinates say where one <em>would</em> be, which is also how you
 * tell the difference between an unexplored crater and an already-generated chunk that predates the
 * plugin.
 */
public class FindCraters implements PlayerCommandExecutor {

  /** How far to look. Wide enough to hold several at the default spacing. */
  private static final int SEARCH_RADIUS = 3000;

  /** How many to list, so the chat stays readable. */
  private static final int LIMIT = 8;

  private final PluginConfig config;
  private final WorldEngine engine;

  /**
   * Creates the executor.
   *
   * @param config the loaded plugin configuration
   * @param engine the running world engine, which owns each world's noise fields
   */
  public FindCraters(PluginConfig config, WorldEngine engine) {
    this.config = config;
    this.engine = engine;
  }

  @Override
  public void run(Player player, CommandArguments args) {
    Location location = player.getLocation();
    int blockX = location.getBlockX();
    int blockZ = location.getBlockZ();

    HugeCraterConfig craters = config.getWorldEngine().getHugeCraters();
    List<CraterSite> sites = CraterSites.around(
        craters,
        engine.fields(player.getWorld()),
        config.getWorldEngine().getThresholds(),
        engine.land(player.getWorld()),
        player.getWorld().getSeed(),
        blockX,
        blockZ,
        SEARCH_RADIUS
    );

    if (sites.isEmpty()) {
      player.sendRichMessage(config.getMessagePrefix()
          + "<gray>No huge craters within " + SEARCH_RADIUS
          + " blocks. They only land where the war map says the fighting reached.");
      return;
    }

    player.sendRichMessage(String.format(
        Locale.ROOT,
        "%s<gray>%d huge crater%s within %d blocks:",
        config.getMessagePrefix(),
        sites.size(),
        sites.size() == 1 ? "" : "s",
        SEARCH_RADIUS
    ));

    for (int i = 0; i < Math.min(LIMIT, sites.size()); i++) {
      describe(player, sites.get(i), craters, blockX, blockZ);
    }
  }

  private void describe(
      Player player,
      CraterSite site,
      HugeCraterConfig craters,
      int blockX,
      int blockZ
  ) {
    int depth = (int) Math.round(site.radius() * craters.getDepthFactor());

    player.sendRichMessage(String.format(
        Locale.ROOT,
        "<gray>  %s  %d blocks %s  <dark_gray>%d wide, ~%d deep",
        TeleportLink.to(site.centerX(), site.centerZ()),
        Math.round(site.distanceTo(blockX, blockZ)),
        Bearing.of(site.centerX() - (double) blockX, site.centerZ() - (double) blockZ),
        site.radius() * 2,
        depth
    ));
  }
}
