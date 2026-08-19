package com.edysmajler.neweracore.command;

import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.structures.StructureSite;
import com.edysmajler.neweracore.world.structures.StructureSites;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.PlayerCommandExecutor;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Finds the nearest scattered structure of a given kind.
 *
 * <p>Vanilla has {@code /locate} for its structures, and these need the same thing for the same
 * reason: they are rare on purpose, and a destination nobody can find is just a coordinate the
 * generator knows about.
 *
 * <p>Everything here is arithmetic over the seed, so it can answer about ground that has never
 * been generated and never loads a chunk to do it — which is also why the answer is where the site
 * <em>will</em> be. A listed site in terrain that generated before the plugin will not be there;
 * one in unexplored terrain appears when the land around it does.
 */
public class Locate implements PlayerCommandExecutor {

  /** Name of the argument holding what to look for. */
  public static final String TARGET = "target";

  /** How far to look, in blocks. Wide enough to hold several sites at the default spacing. */
  private static final int SEARCH_RADIUS = 6000;

  private final PluginConfig config;
  private final WorldEngine engine;

  /**
   * Creates the executor.
   *
   * @param config the loaded plugin configuration
   * @param engine the running world engine, which owns the structure registry
   */
  public Locate(PluginConfig config, WorldEngine engine) {
    this.config = config;
    this.engine = engine;
  }

  @Override
  public void run(Player player, CommandArguments args) {
    String target = String.valueOf(args.get(TARGET)).toLowerCase(Locale.ROOT);

    if (engine.structures().byId(target).isEmpty()) {
      player.sendRichMessage(config.getMessagePrefix()
          + "<gray>Nothing of that kind exists. Try one of: <white>"
          + String.join("<gray>, <white>", engine.structures().ids()));
      return;
    }

    Location location = player.getLocation();
    int blockX = location.getBlockX();
    int blockZ = location.getBlockZ();
    World world = player.getWorld();

    List<StructureSite> found = StructureSites.around(
        config.getWorldEngine().getStructures(),
        engine.structures(),
        engine.land(world),
        world.getSeed(),
        blockX,
        blockZ,
        SEARCH_RADIUS
    ).stream().filter(site -> site.structureId().equals(target)).toList();

    if (found.isEmpty()) {
      player.sendRichMessage(String.format(
          Locale.ROOT,
          "%s<gray>No <white>%s</white> within %d blocks. Sites need ground that suits them, so "
              + "some are a long way apart.",
          config.getMessagePrefix(),
          target,
          SEARCH_RADIUS
      ));
      return;
    }

    report(player, found.get(0), blockX, blockZ);
  }

  /**
   * Prints where it is, how far, and which way.
   */
  private void report(Player player, StructureSite site, int blockX, int blockZ) {
    player.sendRichMessage(String.format(
        Locale.ROOT,
        "%s<gray>Nearest <aqua>%s</aqua><gray>: %s  %d blocks %s "
            + "<dark_gray>(footprint radius %d)",
        config.getMessagePrefix(),
        site.structureId(),
        TeleportLink.to(site.centerX(), site.centerZ()),
        Math.round(site.distanceTo(blockX, blockZ)),
        Bearing.of(site.centerX() - (double) blockX, site.centerZ() - (double) blockZ),
        site.radius()
    ));
  }
}
