package com.edysmajler.neweracore.command;

import com.crimsonwarpedcraft.cwcommons.command.BaseCommand;
import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import org.bukkit.plugin.Plugin;

/**
 * The {@code /neweracore} command and its subcommands.
 */
public class NewEraCoreCommand extends BaseCommand {

  /**
   * Builds the command tree.
   *
   * @param config the loaded plugin configuration
   * @param plugin the plugin instance, used for its version
   * @param engine the running world engine, which owns each world's simulated history
   */
  public NewEraCoreCommand(PluginConfig config, Plugin plugin, WorldEngine engine) {
    super(
        new CommandAPICommand("neweracore")
            .withAliases("nec")
            .withPermission("neweracore.admin")
            .withSubcommand(
                new CommandAPICommand("info")
                    .executes(new Info(config, plugin.getPluginMeta().getVersion()))
            )
            // Both need somewhere to stand, so both are player-only
            .withSubcommand(
                new CommandAPICommand("here")
                    .executesPlayer(new Here(config, engine))
            )
            .withSubcommand(
                new CommandAPICommand("craters")
                    .executesPlayer(new FindCraters(config, engine))
            )
            // A literal per kind rather than free text, so the client completes them and a typo
            // never reaches the executor
            .withSubcommand(
                new CommandAPICommand("locate")
                    .withArguments(new MultiLiteralArgument(Locate.TARGET, Locate.targets()))
                    .executesPlayer(new Locate(config, engine))
            )
    );
  }
}
