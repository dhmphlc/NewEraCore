package com.edysmajler.neweracore.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * One weighted thing a loot pool can produce.
 *
 * <p>Ranges are written the way people think about them — {@code count: 2-5} — rather than as
 * min/max pairs, so a table stays one readable line per item. The pattern here only checks shape;
 * meaning (which material, clamping) is applied where the tables are built, in
 * {@code LootTables}, so a typo costs one entry and a log line rather than the enable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LootEntryConfig {

  /** "3" or "2-5". */
  private static final String RANGE = "\\s*\\d+(\\.\\d+)?(\\s*-\\s*\\d+(\\.\\d+)?)?\\s*";

  @NotBlank
  @JsonProperty("item")
  private String item;

  @DecimalMin("0.001")
  @DecimalMax("1000.0")
  @JsonProperty("weight")
  private double weight = 1.0;

  @NotBlank
  @Pattern(regexp = RANGE)
  @JsonProperty("count")
  private String count = "1";

  @Pattern(regexp = RANGE)
  @JsonProperty("wear")
  private String wear;

  @Pattern(regexp = RANGE)
  @JsonProperty("enchant")
  private String enchant;

  /**
   * Returns the item's material name, or the reserved {@code nothing}.
   *
   * @return the item name
   */
  public String getItem() {
    return item;
  }

  /**
   * Returns this entry's share of the pool's draw.
   *
   * @return the weight
   */
  public double getWeight() {
    return weight;
  }

  /**
   * Returns the stack size range, like {@code 1} or {@code 2-5}.
   *
   * @return the count range
   */
  public String getCount() {
    return count;
  }

  /**
   * Returns the share of durability already lost, like {@code 0.2-0.7}, or null for pristine.
   *
   * @return the wear range, or null
   */
  public String getWear() {
    return wear;
  }

  /**
   * Returns the enchanting-table levels range, like {@code 10-25}, or null for never enchanted.
   *
   * @return the enchant range, or null
   */
  public String getEnchant() {
    return enchant;
  }
}
