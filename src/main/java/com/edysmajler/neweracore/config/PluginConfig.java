package com.edysmajler.neweracore.config;

import com.crimsonwarpedcraft.cwcommons.config.Config;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Represents the NewEraCore configuration loaded from config.yml.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginConfig implements Config {

  @NotBlank
  @JsonProperty("message-prefix")
  private String messagePrefix = "<gray>[<aqua>NewEraCore<gray>]</gray> ";

  @Valid
  @NotNull
  @JsonProperty("world-engine")
  private WorldEngineConfig worldEngine = new WorldEngineConfig();

  PluginConfig() {}

  PluginConfig(String messagePrefix) {
    this.messagePrefix = messagePrefix;
  }

  public String getMessagePrefix() {
    return messagePrefix;
  }

  public WorldEngineConfig getWorldEngine() {
    return worldEngine;
  }
}
