package com.edysmajler.neweracore.command;

import com.crimsonwarpedcraft.cwcommons.command.BaseCommand;
import com.edysmajler.neweracore.config.PluginConfig;
import dev.jorel.commandapi.CommandAPICommand;
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
   */
  public NewEraCoreCommand(PluginConfig config, Plugin plugin) {
    super(
        new CommandAPICommand("neweracore")
            .withAliases("nec")
            .withPermission("neweracore.admin")
            .withSubcommand(
                new CommandAPICommand("info")
                    .executes(new Info(config, plugin.getPluginMeta().getVersion()))
            )
    );
  }
}
