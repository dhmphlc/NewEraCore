package com.edysmajler.neweracore.world.history;

import com.edysmajler.neweracore.config.HistoryConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds history settings for tests from the same keys a server operator would write.
 *
 * <p>Going through the mapper rather than through setters is deliberate: it exercises the property
 * names as well as the behaviour, so a key renamed in the class but not in config.yml fails here
 * instead of silently ignoring whatever the operator asked for.
 */
final class HistoryConfigs {

  private HistoryConfigs() {}

  /**
   * Returns settings with every influence at zero, which should leave the engine untouched.
   *
   * @return the silenced settings
   */
  static HistoryConfig silenced() {
    return read("{"
        + "\"war-influence\": 0.0,"
        + "\"ashfall-influence\": 0.0,"
        + "\"restoration-influence\": 0.0"
        + "}");
  }

  /**
   * Returns settings parsed from JSON, using the same property names as config.yml.
   *
   * @param json the settings
   * @return the parsed config
   */
  static HistoryConfig read(String json) {
    try {
      return new ObjectMapper().readValue(json, HistoryConfig.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("test history config did not parse", e);
    }
  }
}
