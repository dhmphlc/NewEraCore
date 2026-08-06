package com.edysmajler.neweracore.command;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edysmajler.neweracore.config.PluginConfig;
import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class InfoTest {

  @Test
  void sendsPrefixedVersion() {
    PluginConfig config = mock(PluginConfig.class);
    when(config.getMessagePrefix()).thenReturn("[Core] ");
    CommandSender sender = mock(CommandSender.class);

    new Info(config, "1.2.3").run(sender, mock(CommandArguments.class));

    verify(sender).sendRichMessage("[Core] NewEraCore <aqua>1.2.3</aqua>");
  }
}
