package com.edysmajler.neweracore.world.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.WorldEngineConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Asserts what siting costs, because the cost is what decides whether it can know anything.
 *
 * <p>Every question about the ground is a question to the world generator, and siting used to
 * repeat itself twice over. Dry land is the same answer for every candidate type, so it was asked
 * once per type — seven identical questions about one block. And nothing was remembered, so a cell
 * 1500 blocks across was resolved from scratch for each of the nine thousand chunks inside it.
 *
 * <p>That is not a tidiness complaint. Terrain-aware siting means describing the ground around a
 * site rather than testing one point, and the only reason that is affordable is that the
 * description is built once. These tests are what stop the repetition coming back and quietly
 * making it unaffordable again.
 */
class LandmarkSitingCostTest {

  private static final long SEED = 20260806L;

  /** A ground that counts what it is asked. */
  private static final class CountingGround implements SiteTerrain {

    private final boolean dry;
    private int dryLandAsked;
    private int watersideAsked;
    private int openAsked;

    private CountingGround(boolean dry) {
      this.dry = dry;
    }

    @Override
    public boolean isDryLand(int blockX, int blockZ) {
      dryLandAsked++;
      return dry;
    }

    @Override
    public boolean isWaterside(int blockX, int blockZ) {
      watersideAsked++;
      return true;
    }

    @Override
    public boolean isOpen(int blockX, int blockZ) {
      openAsked++;
      return true;
    }
  }

  private static LandmarkMap mapWith(SiteTerrain terrain) {
    WorldEngineConfig config = new WorldEngineConfig();

    return new LandmarkMap(
        SEED,
        config.getHistory(),
        new HistoryMaps(SEED, config.getHistory()),
        terrain
    );
  }

  @Test
  void dryGroundIsAskedOncePerCellNotOncePerCandidateType() {
    CountingGround ground = new CountingGround(true);
    LandmarkMap landmarks = mapWith(ground);

    // Walk cells until one of them holds a site, so the candidate loop actually runs. A vacant cell
    // never reaches the ground at all.
    int cell = 0;
    while (landmarks.siteIn(cell, 0).isEmpty() && cell < 64) {
      cell++;
    }

    assertTrue(cell < 64, "no occupied cell found to measure");
    assertEquals(
        1,
        ground.dryLandAsked,
        "dry land was asked " + ground.dryLandAsked + " times to resolve one occupied cell"
    );
  }

  @Test
  void siteAtSeaCostsOneQuestionAndNoMore() {
    CountingGround ground = new CountingGround(false);
    LandmarkMap landmarks = mapWith(ground);

    assertEquals(Optional.empty(), landmarks.siteIn(3, 7), "something was sited at sea");
    // Most rejected cells in an ocean world are rejected right here, so resolving a story and a
    // full candidate list on the way to placing nothing is the common case, not the rare one.
    assertEquals(1, ground.dryLandAsked);
    assertEquals(0, ground.watersideAsked, "the surroundings of a site at sea were sampled anyway");
    assertEquals(0, ground.openAsked, "the surroundings of a site at sea were sampled anyway");
  }

  @Test
  void cellIsResolvedOnceHoweverManyChunksAskAboutIt() {
    CountingGround ground = new CountingGround(true);
    LandmarkMap landmarks = mapWith(ground);

    landmarks.near(0, 0);
    int afterFirstChunk = ground.dryLandAsked;

    assertTrue(afterFirstChunk > 0, "the ground was never consulted at all");

    // Every chunk in the cell asks the same nine cells. Before the memo this multiplied the cost by
    // the number of chunks; the whole point is that it now costs nothing.
    for (int chunkX = 0; chunkX < 40; chunkX++) {
      for (int chunkZ = 0; chunkZ < 40; chunkZ++) {
        landmarks.near(chunkX * 16 + 8, chunkZ * 16 + 8);
      }
    }

    assertEquals(
        afterFirstChunk,
        ground.dryLandAsked,
        "sixteen hundred chunks re-resolved cells that cannot have changed"
    );
  }

  @Test
  void rememberingCellsNeverChangesWhichLandmarksExist() {
    LandmarkMap fresh = mapWith(SiteTerrain.ANYWHERE);
    LandmarkMap worked = mapWith(SiteTerrain.ANYWHERE);

    // Push the second map past the point where it drops what it remembers, so the comparison covers
    // a map that has forgotten and recomputed rather than one that never filled.
    for (int cellX = -40; cellX < 40; cellX++) {
      for (int cellZ = -40; cellZ < 40; cellZ++) {
        worked.siteIn(cellX, cellZ);
      }
    }

    List<Optional<Landmark>> before = new ArrayList<>();
    List<Optional<Landmark>> after = new ArrayList<>();

    for (int cellX = -6; cellX < 6; cellX++) {
      for (int cellZ = -6; cellZ < 6; cellZ++) {
        before.add(fresh.siteIn(cellX, cellZ));
        after.add(worked.siteIn(cellX, cellZ));
      }
    }

    assertEquals(before, after, "remembering a cell changed the answer for it");
  }

  @Test
  void everyCellKeepsItsOwnAnswer() {
    LandmarkMap landmarks = mapWith(SiteTerrain.ANYWHERE);
    List<Optional<Landmark>> first = new ArrayList<>();

    // Packing two ints into one key is the sort of thing that collides on negatives if it is done
    // carelessly, and a collision here would put one cell's landmark in another cell.
    for (int cellX = -3; cellX < 3; cellX++) {
      for (int cellZ = -3; cellZ < 3; cellZ++) {
        first.add(landmarks.siteIn(cellX, cellZ));
      }
    }

    List<Optional<Landmark>> again = new ArrayList<>();
    for (int cellX = -3; cellX < 3; cellX++) {
      for (int cellZ = -3; cellZ < 3; cellZ++) {
        again.add(landmarks.siteIn(cellX, cellZ));
      }
    }

    assertEquals(first, again);
    assertEquals(
        first.stream().distinct().count() > 1,
        true,
        "every cell gave the same answer, so the key is collapsing them together"
    );
  }
}
