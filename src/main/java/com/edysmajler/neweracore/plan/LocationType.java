package com.edysmajler.neweracore.plan;

/**
 * The kinds of place a designer can put on the map.
 *
 * <p>Each type carries its own opinion of the ground, because that opinion is the difference
 * between a plan and a scatter of coordinates. A dam wants a narrow enclosed valley with water in
 * it; an airport wants the flattest ground in the region; a radio tower wants to stand above
 * everything. Encoding those as {@link #rate} lets the planner warn while you are still choosing,
 * which is the only time the warning is cheap.
 *
 * <p>The judgements are advisory on purpose. The planner shows them and places the marker anyway:
 * this is a hand-authored world, and the designer overruling a heuristic is the normal case, not an
 * error. What must never happen is the other shape of this — a builder that refuses a site on
 * arrival, after the plan has been made around it.
 */
public enum LocationType {

  /** A small settlement. */
  TOWN("Town", 180),

  /** A large settlement. */
  CITY("City", 420),

  /** A military installation. */
  MILITARY_BASE("Military Base", 220),

  /** A hardened underground shelter. */
  BUNKER("Bunker", 60),

  /** An industrial plant. */
  FACTORY("Factory", 140),

  /** A hospital complex. */
  HOSPITAL("Hospital", 90),

  /** A dam across a valley. */
  DAM("Dam", 120),

  /** An airfield. */
  AIRPORT("Airport", 300),

  /** A transmitter mast. */
  RADIO_TOWER("Radio Tower", 40),

  /** Where something came down. */
  CRASH_SITE("Crash Site", 80),

  /** A station on the rail network. */
  RAILWAY_STATION("Railway Station", 70),

  /** A roadside filling station. */
  GAS_STATION("Gas Station", 40);

  /** How well a position suits a type. */
  public enum Rating {

    /** The ground is what this type wants. */
    GOOD,

    /** Workable, with something to fight. */
    FAIR,

    /** The ground is wrong for this. */
    POOR
  }

  /**
   * A judgement of one position for one type.
   *
   * @param rating how well it suits
   * @param reason a short phrase naming what decided it
   */
  public record Suitability(Rating rating, String reason) {}

  private final String label;
  private final int defaultRadius;

  LocationType(String label, int defaultRadius) {
    this.label = label;
    this.defaultRadius = defaultRadius;
  }

  /**
   * Returns the name to show in a toolbar or a marker label.
   *
   * @return the label
   */
  public String label() {
    return label;
  }

  /**
   * Returns the radius a newly placed marker of this type starts with, in blocks.
   *
   * @return the default radius
   */
  public int defaultRadius() {
    return defaultRadius;
  }

  /**
   * Judges a position for this type.
   *
   * @param reading the ground at the position
   * @return the judgement, with the reason that decided it
   */
  public Suitability rate(TerrainReading reading) {
    if (reading.height() == WorldSnapshot.UNKNOWN_HEIGHT) {
      return new Suitability(Rating.FAIR, "outside the surveyed area");
    }

    if (this == DAM) {
      return rateDam(reading);
    }

    if (reading.isWater()) {
      return new Suitability(Rating.POOR, "in open water");
    }

    return switch (this) {
      case RADIO_TOWER -> rateTower(reading);
      case BUNKER -> rateBunker(reading);
      case AIRPORT, MILITARY_BASE -> rateFlatSpread(reading, 2);
      case CITY, TOWN, FACTORY, RAILWAY_STATION -> rateSettlement(reading);
      case CRASH_SITE -> new Suitability(Rating.GOOD, "anything can come down anywhere");
      default -> rateFlatSpread(reading, 4);
    };
  }

  private static Suitability rateDam(TerrainReading reading) {
    if (reading.waterDistance() < 0 || reading.waterDistance() > 64) {
      return new Suitability(Rating.POOR, "no water to hold back");
    }
    if (reading.enclosure() >= 0.6 && reading.slope() >= 3) {
      return new Suitability(Rating.GOOD, "narrow enclosed valley");
    }
    if (reading.enclosure() >= 0.35) {
      return new Suitability(Rating.FAIR, "shallow valley, a long wall");
    }
    return new Suitability(Rating.POOR, "open ground, nothing to dam");
  }

  private static Suitability rateTower(TerrainReading reading) {
    if (reading.relief() >= 6) {
      return new Suitability(Rating.GOOD, "stands over the country around it");
    }
    if (reading.enclosure() >= 0.6) {
      return new Suitability(Rating.POOR, "boxed in by higher ground");
    }
    return new Suitability(Rating.FAIR, "no commanding height");
  }

  private static Suitability rateBunker(TerrainReading reading) {
    if (reading.waterDistance() >= 0 && reading.waterDistance() <= 16) {
      return new Suitability(Rating.POOR, "would flood");
    }
    if (reading.relief() >= 2 || reading.rugged()) {
      return new Suitability(Rating.GOOD, "rock to dig into");
    }
    return new Suitability(Rating.FAIR, "flat ground, deep excavation");
  }

  private static Suitability rateFlatSpread(TerrainReading reading, int tolerance) {
    if (reading.slope() <= tolerance) {
      return new Suitability(Rating.GOOD, "flat enough to build across");
    }
    if (reading.slope() <= tolerance * 3) {
      return new Suitability(Rating.FAIR, "needs terracing");
    }
    return new Suitability(Rating.POOR, "too steep");
  }

  private static Suitability rateSettlement(TerrainReading reading) {
    if (reading.rugged() && reading.slope() > 6) {
      return new Suitability(Rating.POOR, "broken ground");
    }
    if (reading.slope() <= 3 && reading.waterDistance() >= 0 && reading.waterDistance() <= 256) {
      return new Suitability(Rating.GOOD, "level ground near water");
    }
    if (reading.slope() <= 5) {
      return new Suitability(Rating.FAIR, "workable, dry");
    }
    return new Suitability(Rating.POOR, "too steep to lay streets");
  }
}
