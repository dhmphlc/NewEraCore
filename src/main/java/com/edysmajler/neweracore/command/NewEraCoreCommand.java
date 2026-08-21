package com.edysmajler.neweracore.command;

import com.crimsonwarpedcraft.cwcommons.command.BaseCommand;
import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import java.util.stream.Stream;
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
   * @param engine the running world engine, which owns the noise fields and structure registry
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
            // Sampling reads the world the player is standing in, so it is player-only too
            .withSubcommand(
                new CommandAPICommand("export")
                    .withOptionalArguments(new IntegerArgument(Export.SIZE, 256, 16384))
                    .withOptionalArguments(new IntegerArgument(Export.RESOLUTION, 1, 16))
                    .executesPlayer(new Export(config, plugin, engine))
            )
            .withSubcommand(
                new CommandAPICommand("craters")
                    .executesPlayer(new FindCraters(config, engine))
            )
            // A literal per kind rather than free text, so the client completes them and a typo
            // never reaches the executor. The kinds come from the live registry, so a schematic
            // dropped into the structures folder is completable without touching this class.
            .withSubcommand(
                new CommandAPICommand("locate")
                    .withArguments(new MultiLiteralArgument(
                        Locate.TARGET,
                        Stream.concat(
                            engine.structures().ids().stream(),
                            Stream.of(Locate.TOWN)
                        ).toArray(String[]::new)))
                    .executesPlayer(new Locate(config, engine))
            )
    );
  }
}
