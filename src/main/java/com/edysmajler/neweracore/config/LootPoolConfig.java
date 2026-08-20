package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One themed draw within a loot table: roll {@code rolls} times, pick a weighted entry each time.
 *
 * <p>A table is a list of these; the pool split is what keeps a chest coherent — bulk goods in one
 * pool, a single gear piece in another, the long shot in a third — see {@code LootPool}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LootPoolConfig {

  @NotBlank
  @Pattern(regexp = "\\s*\\d+(\\s*-\\s*\\d+)?\\s*")
  @JsonProperty("rolls")
  private String rolls = "1";

  @Valid
  @NotNull
  @JsonProperty("entries")
  private List<@Valid LootEntryConfig> entries = new ArrayList<>();

  /**
   * Returns how many times this pool draws, like {@code 1} or {@code 2-4}; a 0 lower bound lets
   * the pool sit empty sometimes.
   *
   * @return the rolls range
   */
  public String getRolls() {
    return rolls;
  }

  /**
   * Returns the weighted entries this pool draws from.
   *
   * @return the entries
   */
  public List<LootEntryConfig> getEntries() {
    return Collections.unmodifiableList(entries);
  }
}
