package com.edysmajler.neweracore.planner;

import com.edysmajler.neweracore.plan.LocationType;
import com.edysmajler.neweracore.plan.PlannedLocation;
import com.edysmajler.neweracore.plan.PlannedRoad;
import com.edysmajler.neweracore.plan.WorldPlan;
import com.edysmajler.neweracore.plan.WorldSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The plan being edited: a mutable working copy of a {@link WorldPlan}.
 *
 * <p>Separate from the record it saves to because editing and storage want opposite things. The
 * file wants immutability, so a plan on disk is a fixed statement of intent; the editor wants to
 * move a town around with the mouse. Keeping the mutable copy here means the record never grows
 * setters, and the moment of conversion — {@link #toPlan} — is also the moment the plan is
 * validated into shape.
 */
public final class PlanDocument {

  private final List<PlannedLocation> locations = new ArrayList<>();
  private final List<PlannedRoad> roads = new ArrayList<>();

  private long seed;
  private int originX;
  private int originZ;
  private int size;
  private boolean dirty;

  /**
   * Creates an empty plan over the area a snapshot covers.
   *
   * @param snapshot the terrain the plan is designed against
   */
  public PlanDocument(WorldSnapshot snapshot) {
    load(WorldPlan.emptyFor(snapshot));
    this.dirty = false;
  }

  /**
   * Replaces everything with the contents of a plan.
   *
   * @param plan the plan to take over
   */
  public void load(WorldPlan plan) {
    locations.clear();
    locations.addAll(plan.locations());
    roads.clear();
    roads.addAll(plan.roads());
    seed = plan.seed();
    originX = plan.originX();
    originZ = plan.originZ();
    size = plan.size();
    dirty = false;
  }

  /**
   * Returns the plan as it would be saved.
   *
   * @return an immutable plan
   */
  public WorldPlan toPlan() {
    return new WorldPlan(seed, originX, originZ, size, locations, roads);
  }

  /**
   * Returns the locations, in the order they were placed.
   *
   * @return the locations
   */
  public List<PlannedLocation> locations() {
    return List.copyOf(locations);
  }

  /**
   * Returns the planned connections.
   *
   * @return the roads
   */
  public List<PlannedRoad> roads() {
    return List.copyOf(roads);
  }

  /**
   * Returns whether there are unsaved changes.
   *
   * @return true when the plan differs from what was last loaded or saved
   */
  public boolean isDirty() {
    return dirty;
  }

  /** Records that the plan has been written out. */
  public void markSaved() {
    dirty = false;
  }

  /**
   * Adds a location at a position, naming it after its type and a running number.
   *
   * @param type what kind of place it is
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the location that was added
   */
  public PlannedLocation add(LocationType type, int blockX, int blockZ) {
    String base = type.name().toLowerCase(Locale.ROOT);
    PlannedLocation location = new PlannedLocation(
        uniqueId(base),
        type,
        type.label() + " " + (countOf(type) + 1),
        blockX,
        blockZ,
        type.defaultRadius(),
        ""
    );

    locations.add(location);
    dirty = true;
    return location;
  }

  /**
   * Replaces a location with an edited copy of itself.
   *
   * @param edited the location, carrying the id of the one it replaces
   * @return true when a location with that id was found
   */
  public boolean replace(PlannedLocation edited) {
    for (int i = 0; i < locations.size(); i++) {
      if (locations.get(i).id().equals(edited.id())) {
        locations.set(i, edited);
        dirty = true;
        return true;
      }
    }

    return false;
  }

  /**
   * Removes a location and every road that referred to it.
   *
   * <p>The roads go too, deliberately. A connection to a place that no longer exists is not a
   * survivable state for the generator to read, and asking the designer to tidy up after every
   * deletion is how such a state gets saved.
   *
   * @param id the location's id
   */
  public void remove(String id) {
    locations.removeIf(location -> location.id().equals(id));
    roads.removeIf(road -> road.fromId().equals(id) || road.toId().equals(id));
    dirty = true;
  }

  /**
   * Connects two locations, or disconnects them when they already are.
   *
   * @param fromId the first location's id
   * @param toId the second location's id
   */
  public void toggleRoad(String fromId, String toId) {
    if (fromId.equals(toId)) {
      return;
    }

    boolean removed = roads.removeIf(road -> connects(road, fromId, toId));
    if (!removed) {
      roads.add(new PlannedRoad(fromId, toId));
    }

    dirty = true;
  }

  /**
   * Finds a location by id.
   *
   * @param id the id to look for
   * @return the location, or empty when nothing has that id
   */
  public Optional<PlannedLocation> byId(String id) {
    return locations.stream().filter(location -> location.id().equals(id)).findFirst();
  }

  /**
   * Finds the location nearest a position, within a distance.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @param withinBlocks how far to look
   * @return the nearest location in range, or empty
   */
  public Optional<PlannedLocation> nearest(int blockX, int blockZ, double withinBlocks) {
    PlannedLocation best = null;
    double bestDistance = withinBlocks;

    for (PlannedLocation location : locations) {
      double distance = location.distanceTo(blockX, blockZ);
      if (distance <= bestDistance) {
        best = location;
        bestDistance = distance;
      }
    }

    return Optional.ofNullable(best);
  }

  private static boolean connects(PlannedRoad road, String a, String b) {
    return (road.fromId().equals(a) && road.toId().equals(b))
        || (road.fromId().equals(b) && road.toId().equals(a));
  }

  private int countOf(LocationType type) {
    return (int) locations.stream().filter(location -> location.type() == type).count();
  }

  private String uniqueId(String base) {
    Set<String> taken = new LinkedHashSet<>();
    locations.forEach(location -> taken.add(location.id()));

    for (int i = 1; ; i++) {
      String candidate = base + "_" + i;
      if (taken.add(candidate)) {
        return candidate;
      }
    }
  }
}
