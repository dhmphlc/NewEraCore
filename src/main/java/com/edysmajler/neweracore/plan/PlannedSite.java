package com.edysmajler.neweracore.plan;

/**
 * A feature the plugin will place, resolved from the seed and written into a snapshot.
 *
 * <p>Sites are exported rather than recomputed by the planner, and that is a deliberate call. Site
 * selection asks a structure whether it {@link
 * com.edysmajler.neweracore.world.structures.StructureDefinition#suits suits} the ground, and each
 * kind answers differently — the fighter jet probes for land where its trench will run, a crashed
 * schematic probes the other way. A planner that reproduced the grid arithmetic would have to
 * reproduce every one of those answers too, and would drift out of agreement with the plugin the
 * first time one of them was refined. Letting the server answer once and shipping the answers keeps
 * the two from ever disagreeing.
 *
 * @param kind which system put this here
 * @param id the structure id, the town's name-less identifier, or the crater's size band
 * @param centerX absolute block x of the centre
 * @param centerZ absolute block z of the centre
 * @param radius how far from the centre the feature reaches, in blocks
 * @param rotation quarter turns clockwise from the authored facing, 0-3; 0 for towns and craters
 */
public record PlannedSite(
    SiteKind kind,
    String id,
    int centerX,
    int centerZ,
    int radius,
    int rotation
) {

  /** Which of the plugin's systems a site came from. */
  public enum SiteKind {

    /** A scattered structure: wreck, ruin, or schematic. */
    STRUCTURE,

    /** A ruined town. */
    TOWN,

    /** A huge crater. */
    CRATER;

    /**
     * Returns the kind for a stored ordinal.
     *
     * @param ordinal the stored ordinal
     * @return the kind
     * @throws IllegalArgumentException when the ordinal names no kind
     */
    public static SiteKind byOrdinal(int ordinal) {
      SiteKind[] all = values();
      if (ordinal < 0 || ordinal >= all.length) {
        throw new IllegalArgumentException("Unknown site kind ordinal: " + ordinal);
      }
      return all[ordinal];
    }
  }
}
