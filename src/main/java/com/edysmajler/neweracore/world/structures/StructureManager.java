package com.edysmajler.neweracore.world.structures;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The registry of everything the scatter system can place.
 *
 * <p>One instance holds the definitions in a fixed order, because the order takes part in the
 * weighted draw: reordering the list would move every structure in every world. Add a structure by
 * registering it here (see {@code WorldEngineFactory}); the siting, triggering, and locate command
 * all read this registry and need no changes.
 */
public final class StructureManager {

  private final Map<String, StructureDefinition> byId = new LinkedHashMap<>();
  private final double totalWeight;
  private final int maxRadius;

  /**
   * Creates a registry over a fixed list of definitions.
   *
   * @param definitions the structures, in registration order
   * @throws IllegalArgumentException when two definitions share an id or a weight is not positive
   */
  public StructureManager(List<StructureDefinition> definitions) {
    double weight = 0.0;
    int radius = 0;

    for (StructureDefinition definition : definitions) {
      if (byId.put(definition.id(), definition) != null) {
        throw new IllegalArgumentException("Duplicate structure id: " + definition.id());
      }
      if (definition.weight() <= 0.0) {
        throw new IllegalArgumentException("Structure weight must be positive: " + definition.id());
      }

      weight += definition.weight();
      radius = Math.max(radius, definition.radius());
    }

    this.totalWeight = weight;
    this.maxRadius = radius;
  }

  /**
   * Returns whether the registry holds anything at all.
   *
   * @return true when no structures are registered
   */
  public boolean isEmpty() {
    return byId.isEmpty();
  }

  /**
   * Returns a definition by its id.
   *
   * @param id the structure id
   * @return the definition, or empty when nothing has that id
   */
  public Optional<StructureDefinition> byId(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  /**
   * Returns every registered id, in registration order.
   *
   * @return the ids
   */
  public List<String> ids() {
    return List.copyOf(byId.keySet());
  }

  /**
   * Returns the largest footprint radius in the registry.
   *
   * <p>The candidate window sites are searched in is derived from this, never picked by eye — a
   * window smaller than the biggest structure makes chunks near a footprint's edge silently skip
   * the site.
   *
   * @return the radius in blocks
   */
  public int maxRadius() {
    return maxRadius;
  }

  /**
   * Draws a definition by weight.
   *
   * @param roll a uniform value in [0, 1)
   * @return the drawn definition
   */
  public StructureDefinition pick(double roll) {
    double target = roll * totalWeight;
    double seen = 0.0;
    StructureDefinition last = null;

    for (StructureDefinition definition : byId.values()) {
      last = definition;
      seen += definition.weight();
      if (target < seen) {
        return definition;
      }
    }

    return last;
  }

  /**
   * Returns the definitions in registration order.
   *
   * @return the definitions
   */
  public List<StructureDefinition> all() {
    return new ArrayList<>(byId.values());
  }
}
