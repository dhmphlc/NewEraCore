package com.edysmajler.neweracore.world.history;

/**
 * What the world generator would put on the ground, asked without generating anything.
 *
 * <p>The seam that lets siting be terrain-aware at the moment it chooses, which is the only moment
 * worth being terrain-aware at. The alternative — letting a builder arrive later and refuse a site
 * because there is no river under its dam — loses the argument before it starts: by then the
 * history layer has already committed to a claim the terrain contradicts, and no amount of
 * graceful failure afterwards puts that back. A dam site should be <em>chosen</em> as river plus
 * valley plus enclosing ground, never rolled and then hoped over.
 *
 * <p>Everything here is a pure function of the world seed, so a landmark stays knowable from
 * arbitrarily far away and every chunk agrees about it without loading a neighbour. That is the
 * same constraint {@code LandLookup} works under, and this is a deliberate widening of it rather
 * than a new idea: one question about a point became a description of an area.
 *
 * <p><strong>There is no height and no slope here, and that is not an omission.</strong> Paper
 * exposes no generator-side height query, so any number this reported would either come from a
 * chunk — which the constraint above forbids — or from a reimplementation of Mojang's density
 * noise, which would disagree with the real terrain wherever it drifted. A coarse answer that is
 * true beats a precise one that lies. So landform is inferred from the spread of computed biomes
 * over an area, which costs nothing and cannot contradict the world it describes.
 *
 * <p>Bukkit-free, like everything else in this package, which is what lets the derivation below be
 * tested against a fake ground rather than a running server.
 */
public interface TerrainQuery {

  /** How many points are sampled around a site on each ring. */
  int SAMPLES = 8;

  /** Close in, where a site's own footing is decided. */
  int NEAR = 24;

  /** Middle distance, matching the reach the old waterside test used. */
  int MID = 72;

  /** Far enough out to say what the place sits inside rather than what it stands on. */
  int FAR = 160;

  /**
   * Ground that suits everything, for tests and for worlds with no generator to ask.
   *
   * <p>Overrides the sampling rather than the classification, because a permissive classification
   * does not produce a permissive answer: open ground everywhere would measure as a dry inland
   * plain and refuse every site that wants water.
   */
  TerrainQuery ANYWHERE = new TerrainQuery() {
    @Override
    public SiteGround at(int blockX, int blockZ) {
      return SiteGround.ANYWHERE;
    }
  };

  /**
   * What kind of ground a single point is.
   *
   * <p>One value rather than a set of predicates, and the reason is cost. Each predicate would be a
   * separate question to the generator about the same block, and a site samples twenty-five points;
   * asking three things about each would triple that for no new information. The categories are
   * mutually exclusive because the underlying answer is a single biome name, and a biome is one
   * thing at a time.
   */
  enum Ground {

    /** Open sea. Nothing stands here. */
    OCEAN,

    /** A river: water that has banks, and therefore something a dam could be built across. */
    RIVER,

    /** Broken ground — peaks, hills, slopes. High, and nothing wants to be built across it. */
    RUGGED,

    /** Everything else: land level enough to build on. */
    OPEN;

    /**
     * Returns whether this ground is water of any kind.
     *
     * @return true for ocean and river
     */
    public boolean isWater() {
      return this == OCEAN || this == RIVER;
    }
  }

  /**
   * Returns what kind of ground the generator puts at a point.
   *
   * <p>Defaults to open land, so an implementation with nothing to say still describes a world that
   * can be built on.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the kind of ground
   */
  default Ground groundAt(int blockX, int blockZ) {
    return Ground.OPEN;
  }

  /**
   * Returns a description of the ground around a site, sampled once.
   *
   * <p>Three rings of eight points and the centre: twenty-five questions to the generator, and
   * every one of them classified in a single pass so no point is asked about twice. Rings rather
   * than a filled grid because what matters is the ground <em>at</em> a distance, not the average
   * of everything inside it — a valley floor and its rim are different answers, and a grid blurs
   * them into one.
   *
   * <p>Water at the centre short-circuits. A site at sea cannot hold anything whatever the
   * surroundings say, and skipping the other twenty-four samples matters because most rejected
   * cells in an ocean world are rejected exactly here.
   *
   * @param blockX absolute block x of the site
   * @param blockZ absolute block z of the site
   * @return what the ground is like
   */
  default SiteGround at(int blockX, int blockZ) {
    if (groundAt(blockX, blockZ).isWater()) {
      return SiteGround.SEA;
    }

    int inner = 0;
    int innerWater = 0;
    int innerRiver = 0;
    int surrounding = 0;
    int surroundingRugged = 0;
    int outer = 0;
    int outerRugged = 0;

    for (int radius : new int[] {NEAR, MID, FAR}) {
      for (int i = 0; i < SAMPLES; i++) {
        double angle = i * 2.0 * Math.PI / SAMPLES;
        int x = blockX + (int) Math.round(Math.cos(angle) * radius);
        int z = blockZ + (int) Math.round(Math.sin(angle) * radius);
        Ground ground = groundAt(x, z);

        // Water is a question about what is in reach, so the far ring does not vote on it: a sea
        // one hundred and sixty blocks away is a view, not something you build against.
        if (radius != FAR) {
          inner++;
          if (ground.isWater()) {
            innerWater++;
          }
          if (ground == Ground.RIVER) {
            innerRiver++;
          }
        }

        // Relief is the opposite question — what the place sits inside — so it skips the near ring,
        // which is close enough to be the site's own footing rather than its setting.
        if (radius != NEAR) {
          surrounding++;
          if (ground == Ground.RUGGED) {
            surroundingRugged++;
          }
        }

        if (radius == FAR) {
          outer++;
          if (ground == Ground.RUGGED) {
            outerRugged++;
          }
        }
      }
    }

    return new SiteGround(
        true,
        share(innerWater, inner),
        share(innerRiver, inner),
        share(surroundingRugged, surrounding),
        share(outerRugged, outer)
    );
  }

  private static double share(int count, int total) {
    return total == 0 ? 0.0 : count / (double) total;
  }
}
