package com.edysmajler.neweracore.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crimsonwarpedcraft.cwcommons.config.bukkit.BukkitConfigManagerBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginConfigIntegrationTest {

  @TempDir
  private Path tempDirectory;

  @Test
  void loadsDefaultConfigWithCwCommons() throws IOException {
    File configFile = copyDefaultConfig();

    PluginConfig config = manager().load(configFile, PluginConfig.class);

    assertEquals("<gray>[<aqua>NewEraCore<gray>]</gray> ", config.getMessagePrefix());
    assertTrue(config.getWorldEngine().isEnabled());
    assertEquals(24, config.getWorldEngine().getScanDepth());
  }

  @Test
  void loadsNestedWorldEngineSectionWithCwCommons() throws IOException {
    File configFile = copyDefaultConfig();

    WorldEngineConfig engine = manager().load(configFile, PluginConfig.class).getWorldEngine();

    // Every nested section must bind, not silently fall back to defaults
    assertEquals(384, engine.getNoise().getCorruptionScale());
    assertEquals(56, engine.getNoise().getBlightScale());
    assertEquals(0.34, engine.getThresholds().getScarredAbove());
    assertEquals(0.68, engine.getThresholds().getDevastatedAbove());
    assertEquals(0.55, engine.getLevels().getRecovered().getAshCarpetCoverage());
    assertEquals(0.35, engine.getLevels().getRecovered().getLivingGroveThreshold());
    assertEquals(1.0, engine.getLevels().getDevastated().getAshCarpetCoverage());
    assertEquals(0.0, engine.getLevels().getDevastated().getLivingGroveThreshold());
    assertEquals(2, engine.getLevels().getDevastated().getScourSlope());
    assertEquals(2.0, engine.getLevels().getDevastated().getCratersPerZone());
    assertEquals(0.95, engine.getLevels().getDevastated().getWaterDryingChance());
    assertEquals(768, engine.getHugeCraters().getSpacing());
    assertEquals(36, engine.getHugeCraters().getRadiusMax());
    assertEquals(0.18, engine.getOres().getHugeCraterChance());
  }

  @Test
  void overridesNestedValuesWithCwCommons() throws IOException {
    Path configFile = tempDirectory.resolve("config.yml");
    Files.writeString(
        configFile,
        """
        message-prefix: "[Core] "
        world-engine:
          enabled: false
          scan-depth: 8
          noise:
            corruption-scale: 512
          levels:
            devastated:
              ash-carpet-coverage: 0.2
        """
    );

    PluginConfig config = manager().load(configFile.toFile(), PluginConfig.class);

    assertEquals("[Core] ", config.getMessagePrefix());
    assertEquals(8, config.getWorldEngine().getScanDepth());
    assertEquals(512, config.getWorldEngine().getNoise().getCorruptionScale());
    assertEquals(0.2, config.getWorldEngine().getLevels().getDevastated()
        .getAshCarpetCoverage());
    // Keys left out of the file keep their defaults, at every nesting depth
    assertEquals(56, config.getWorldEngine().getNoise().getBlightScale());
    assertEquals(0.55, config.getWorldEngine().getLevels().getRecovered()
        .getAshCarpetCoverage());
  }

  @Test
  void rejectsBlankPrefixWithCwCommons() throws IOException {
    Path configFile = tempDirectory.resolve("config.yml");
    Files.writeString(configFile, "message-prefix: ''\n");

    assertThrows(IllegalStateException.class,
        () -> manager().load(configFile.toFile(), PluginConfig.class));
  }

  @Test
  void rejectsChanceAboveOneWithCwCommons() throws IOException {
    Path configFile = tempDirectory.resolve("config.yml");
    Files.writeString(
        configFile,
        """
        world-engine:
          levels:
            scarred:
              ash-carpet-coverage: 1.5
        """
    );

    // Validation must reach two levels down, which only works because each field is @Valid
    assertThrows(IllegalStateException.class,
        () -> manager().load(configFile.toFile(), PluginConfig.class));
  }

  @Test
  void rejectsNoiseScaleOutOfRangeWithCwCommons() throws IOException {
    Path configFile = tempDirectory.resolve("config.yml");
    Files.writeString(
        configFile,
        """
        world-engine:
          noise:
            corruption-scale: 4
        """
    );

    assertThrows(IllegalStateException.class,
        () -> manager().load(configFile.toFile(), PluginConfig.class));
  }

  @Test
  void rejectsScanDepthOutOfRangeWithCwCommons() throws IOException {
    Path configFile = tempDirectory.resolve("config.yml");
    Files.writeString(
        configFile,
        """
        world-engine:
          scan-depth: 0
        """
    );

    assertThrows(IllegalStateException.class,
        () -> manager().load(configFile.toFile(), PluginConfig.class));
  }

  @Test
  void acceptsValidConfigWithCwCommons() {
    PluginConfig config = new PluginConfig("[Core] ");

    assertDoesNotThrow(() -> manager().validate(config));
  }

  @Test
  void rejectsBlankMessagePrefixWithCwCommons() {
    PluginConfig config = new PluginConfig("");

    assertThrows(IllegalStateException.class, () -> manager().validate(config));
  }

  private File copyDefaultConfig() throws IOException {
    Path configFile = tempDirectory.resolve("config.yml");
    try (var config = getClass().getResourceAsStream("/config.yml")) {
      if (config == null) {
        throw new IOException("Default config.yml was not found");
      }
      Files.copy(config, configFile);
    }
    return configFile.toFile();
  }

  private static com.crimsonwarpedcraft.cwcommons.config.ConfigManager manager() {
    return new BukkitConfigManagerBuilder().build();
  }
}
