package com.edysmajler.neweracore.command;

import com.edysmajler.neweracore.config.PluginConfig;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Reports the running NewEraCore version to the command sender.
 */
public class Info implements CommandExecutor {

  private final PluginConfig config;
  private final String version;

  /**
   * Creates a new info executor.
   *
   * @param config the loaded plugin configuration
   * @param version the running plugin version
   */
  public Info(PluginConfig config, String version) {
    this.config = config;
    this.version = version;
  }

  @Override
  public void run(CommandSender sender, CommandArguments args) {
    sender.sendRichMessage(config.getMessagePrefix() + "NewEraCore <aqua>" + version + "</aqua>");
  }
}
