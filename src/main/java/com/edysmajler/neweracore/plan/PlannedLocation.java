package com.edysmajler.neweracore.plan;

/**
 * One place a designer has put on the map.
 *
 * <p>Carries an id of its own rather than being addressed by name or by coordinate, because roads
 * refer to locations and both of the other two change: a town gets renamed, and a town gets nudged
 * two hundred blocks up the valley. Neither should silently break a connection.
 *
 * @param id stable identifier, unique within a plan
 * @param type what kind of place this is
 * @param name what the place is called in game
 * @param blockX absolute block x of its centre
 * @param blockZ absolute block z of its centre
 * @param radius how far it reaches from that centre, in blocks
 * @param notes free text for the designer; never read by the generator
 */
public record PlannedLocation(
    String id,
    LocationType type,
    String name,
    int blockX,
    int blockZ,
    int radius,
    String notes
) {

  /**
   * Fills in the parts a hand-written or older plan file may leave out.
   */
  public PlannedLocation {
    type = type == null ? LocationType.TOWN : type;
    name = name == null ? "" : name;
    notes = notes == null ? "" : notes;
    radius = Math.max(1, radius);
  }

  /**
   * Returns a copy moved to a new position.
   *
   * @param newX absolute block x
   * @param newZ absolute block z
   * @return the moved location
   */
  public PlannedLocation movedTo(int newX, int newZ) {
    return new PlannedLocation(id, type, name, newX, newZ, radius, notes);
  }

  /**
   * Returns the distance from this location's centre to a position.
   *
   * @param x absolute block x
   * @param z absolute block z
   * @return the distance in blocks
   */
  public double distanceTo(int x, int z) {
    return Math.hypot(blockX - (double) x, blockZ - (double) z);
  }
}
