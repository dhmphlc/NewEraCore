package com.edysmajler.neweracore.config;

import com.edysmajler.neweracore.plan.LocationType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.EnumMap;
import java.util.Map;

/**
 * Hand-authored world plans: the designer's decisions, read back at generation time.
 *
 * <p>The seeded scatter answers "where would a wreck plausibly be"; a plan answers "where is
 * Haven". Both exist because they are different questions, and the plan wins where they overlap:
 * a planned location clears the seeded systems out of its own ground, since finding a procedural
 * town four blocks off a designed one is worse than finding neither.
 *
 * <p>Which types can actually be built is deliberately explicit. Only settlements have a builder
 * today, so a plan full of hospitals and factories places nothing and says so in the log rather
 * than silently approximating them with whatever wreck was nearest to hand.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanConfig {

  @JsonProperty("enabled")
  private boolean enabled = true;

  @JsonProperty("file")
  private String file = "";

  @Min(0)
  @Max(512)
  @JsonProperty("clearance")
  private int clearance = 32;

  @JsonProperty("builders")
  private Map<LocationType, String> builders = defaultBuilders();

  /**
   * Returns whether plans are read at all.
   *
   * @return true when a plan file will be looked for
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Returns the plan file to read, relative to the plugin's data folder.
   *
   * <p>Empty means the conventional pair: {@code plans/<world>.json} if it exists, otherwise
   * {@code plans/world-plan.json}. Naming a file makes every world read the same plan, which is
   * only ever right while testing one.
   *
   * @return the path, or an empty string for the convention
   */
  public String getFile() {
    return file;
  }

  /**
   * Returns how far beyond a planned location's radius the seeded systems are kept out.
   *
   * @return the clearance in blocks
   */
  public int getClearance() {
    return clearance;
  }

  /**
   * Returns which registered structure builds each planned type.
   *
   * <p>Settlements are absent from this map on purpose: they are built by the town system rather
   * than by a structure, and a mapping here cannot override that.
   *
   * @return the type-to-structure mapping
   */
  public Map<LocationType, String> getBuilders() {
    return Map.copyOf(builders);
  }

  /**
   * Returns the structure that builds a planned type, if any.
   *
   * @param type the planned type
   * @return the structure id, or null when nothing builds this type yet
   */
  public String builderFor(LocationType type) {
    return builders.get(type);
  }

  private static Map<LocationType, String> defaultBuilders() {
    Map<LocationType, String> defaults = new EnumMap<>(LocationType.class);
    // The one built-in wreck, so a fresh install can place something a designer sited by hand
    defaults.put(LocationType.CRASH_SITE, "fighter_jet");
    return defaults;
  }
}
