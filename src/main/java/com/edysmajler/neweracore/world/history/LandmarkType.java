package com.edysmajler.neweracore.world.history;

import com.edysmajler.neweracore.world.infrastructure.RouteType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The kinds of place worth walking to.
 *
 * <p>Each type carries a footprint — the radius a future structure generator may claim — and an
 * affinity for the region stories it belongs in. The affinity is what stops landmarks from reading
 * as scenery scattered by a dice roll: a missile silo in a valley the war never reached says
 * nothing, while the same silo in the middle of a crater field explains the crater field.
 *
 * <p>An empty affinity means the type fits anywhere, which guarantees the candidate list is never
 * empty for any story.
 *
 * <p><strong>No structures are generated from this yet.</strong> These are locations and intentions
 * only; the generators that build them come later and will read the same {@link RegionProfile} as
 * everything else.
 */
public enum LandmarkType {

  /** The reason a region became a front line. */
  MISSILE_SILO(24, RouteType.HIGHWAY, RegionStory.FRONT_LINE, RegionStory.ASHEN_WASTE),

  /** Barracks, motor pool, wire. Where the fighting was organised from. */
  MILITARY_BASE(48, RouteType.HIGHWAY, RegionStory.FRONT_LINE, RegionStory.RUINED_TOWNS),

  /** Whatever was being worked on when it stopped. Supplied by rail, like anything industrial. */
  RESEARCH_FACILITY(32, RouteType.RAILWAY, RegionStory.ASHEN_WASTE, RegionStory.FRONT_LINE),

  /** Runways long enough to see from a ridge, and level enough to have been landed on. */
  AIRPORT(180, RouteType.HIGHWAY, RegionStory.RUINED_TOWNS, RegionStory.DUST_BOWL),

  /** Where the people who could not leave were taken. */
  HOSPITAL(28, RouteType.HIGHWAY, RegionStory.RUINED_TOWNS, RegionStory.DUST_BOWL),

  /** Still holding a river back, whether or not anyone wants it to. Where the power came from. */
  HYDROELECTRIC_DAM(40, RouteType.POWER_LINE, RegionStory.GREEN_REFUGE, RegionStory.DUST_BOWL),

  /** Small, cheap, and everywhere: the one thing that might still be transmitting. */
  RADIO_TOWER(12, RouteType.POWER_LINE);

  private final int footprint;
  private final RouteType connectsBy;
  private final Set<RegionStory> affinity;

  LandmarkType(int footprint, RouteType connectsBy, RegionStory... affinity) {
    this.footprint = footprint;
    this.connectsBy = connectsBy;
    this.affinity = Set.of(affinity);
  }

  /**
   * Returns the radius in blocks a structure of this type may claim.
   *
   * @return the footprint radius
   */
  public int footprint() {
    return footprint;
  }

  /**
   * Returns the kind of connection this place wants.
   *
   * <p>A works needs rail, a base needs a road, a dam is the far end of a power line. What a place
   * is decides what runs to it, which is what makes the network read as consequence rather than
   * decoration.
   *
   * @return the route type
   */
  public RouteType connectsBy() {
    return connectsBy;
  }

  /**
   * Returns whether this kind of place could have been built on this ground.
   *
   * <p>The counterpart to {@link #fits}: that asks whether the region's history suits the place,
   * this asks whether the ground does. A dam wants a river to hold back and a bridge wants water to
   * cross, and neither means anything without one. An airport wants open country — its runway can
   * be levelled wherever it lands, but the ridge an aircraft would have to fly through on approach
   * cannot be, so the answer is not to put it in the hills at all.
   *
   * <p>Everything else can stand anywhere, which also guarantees the candidate list is never empty:
   * {@code RADIO_TOWER} has no story affinity and no ground requirement, so it always survives both
   * filters.
   *
   * @param terrain what the ground is like
   * @param blockX absolute block x of the site
   * @param blockZ absolute block z of the site
   * @return true when the place could stand here
   */
  public boolean canStandAt(SiteTerrain terrain, int blockX, int blockZ) {
    return terrain.isDryLand(blockX, blockZ) && suitsGround(terrain, blockX, blockZ);
  }

  /**
   * Returns whether the ground suits this type, for a caller that has already established it is
   * dry.
   *
   * <p>The type-specific half of {@link #canStandAt}, split out because dry land is the same answer
   * for every candidate type and asking it inside the loop asked the generator the same question
   * about the same block once per type. Siting resolves a cell by asking about the ground once and
   * then calling this per candidate; the whole-question form above stays for callers who only have
   * one type to test.
   *
   * @param terrain what the ground is like
   * @param blockX absolute block x of the site
   * @param blockZ absolute block z of the site
   * @return true when the ground suits this type, dry land assumed
   */
  public boolean suitsGround(SiteTerrain terrain, int blockX, int blockZ) {
    return switch (this) {
      case HYDROELECTRIC_DAM -> terrain.isWaterside(blockX, blockZ);
      case AIRPORT -> terrain.isOpen(blockX, blockZ);
      default -> true;
    };
  }

  /**
   * Returns how well described ground suits this type, from 0 to 1.
   *
   * <p>The continuous form of {@link #canStandAt}, and it lives here rather than in the command
   * that prints it so the number a player is shown is the same number siting will act on. A readout
   * that computes its own answer is worse than no readout: it agrees right up until the moment the
   * two drift, and then it lies about the one thing it exists to explain.
   *
   * <p>Types with no ground requirement score 1 anywhere dry, which is honest rather than a
   * placeholder — a radio mast really does not care. Nothing scores anything at sea.
   *
   * @param ground what the ground around the site is like
   * @return 0 where the type could not stand, 1 where the ground is everything it wants
   */
  public double suitability(SiteGround ground) {
    if (!ground.dryLand()) {
      return 0.0;
    }

    return switch (this) {
      case HYDROELECTRIC_DAM -> ground.valley();
      case AIRPORT -> ground.openness();
      default -> 1.0;
    };
  }

  /**
   * Returns whether this type's suitability depends on the ground at all.
   *
   * <p>So a readout can list the types whose score means something instead of a column of ones.
   *
   * @return true when the type has a ground requirement
   */
  public boolean needsParticularGround() {
    return this == HYDROELECTRIC_DAM || this == AIRPORT;
  }

  /**
   * Returns whether this type belongs in a region with the given story.
   *
   * @param story the region's story
   * @return true when the type fits, always true for types with no affinity
   */
  public boolean fits(RegionStory story) {
    return affinity.isEmpty() || affinity.contains(story);
  }

  /**
   * Returns every type that belongs in a region with the given story.
   *
   * <p>Never empty, and always in declaration order so the choice stays deterministic.
   *
   * @param story the region's story
   * @return the candidate types
   */
  public static List<LandmarkType> fitting(RegionStory story) {
    List<LandmarkType> candidates = new ArrayList<>();

    for (LandmarkType type : values()) {
      if (type.fits(story)) {
        candidates.add(type);
      }
    }

    return List.copyOf(candidates);
  }
}
