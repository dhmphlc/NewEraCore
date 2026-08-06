package com.edysmajler.neweracore.world.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.HistoryConfig;
import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Asserts the observable variety of the simulated history, not that the classes exist.
 *
 * <p>Written in the same spirit as {@code CorruptionStatisticsTest}, and for the same reason: the
 * two failures that shipped in this engine both looked correct in the code and were only catchable
 * by measuring the world they produced. "No two regions feel identical" and "the player keeps
 * meeting contrast" are claims about a distribution, so they are tested as claims about a
 * distribution.
 */
class HistoryStatisticsTest {

  private static final long SEED = 20260806L;
  private static final int STRIDE = 96;
  private static final int EDGE = 220;

  private final WorldEngineConfig config = new WorldEngineConfig();
  private final HistoryConfig history = config.getHistory();
  private final HistoryEngine engine = new HistoryEngine(SEED, config);

  @Test
  void everyStoryCoversMeaningfulGround() {
    Map<RegionStory, Integer> counts = storyCounts();
    int total = counts.values().stream().mapToInt(Integer::intValue).sum();

    for (RegionStory story : RegionStory.values()) {
      double share = counts.getOrDefault(story, 0) / (double) total;

      // A story nobody ever walks into is a story that does not exist, and one that covers half the
      // world is the monotony this layer was built to break.
      assertTrue(share > 0.06, story + " covers only " + percent(share) + " of the world");
      assertTrue(share < 0.40, story + " covers " + percent(share) + ", crowding out the others");
    }
  }

  @Test
  void theStoryChangesOftenEnoughToKeepWalkingInteresting() {
    double meanRun = meanStoryRunLength();

    // The three layers are deliberately different widths so they beat against each other instead of
    // peaking together. If they ever get set to similar scales, this is the test that notices: the
    // combination would stop changing and the world would become a handful of vast uniform
    // districts.
    assertTrue(meanRun < 2500.0, "story holds for " + Math.round(meanRun) + " blocks at a time");
    // The opposite failure: a story that changes every few blocks is noise, not history
    assertTrue(meanRun > 120.0, "story changes every " + Math.round(meanRun) + " blocks");
  }

  @Test
  void greenSurvivesInsideWarZones() {
    int warTorn = 0;
    int greenInsideWar = 0;

    for (int x = 0; x < EDGE; x++) {
      for (int z = 0; z < EDGE; z++) {
        int blockX = x * STRIDE;
        int blockZ = z * STRIDE;

        if (engine.maps().war().at(blockX, blockZ) < history.getWarHigh()) {
          continue;
        }

        warTorn++;
        if (engine.maps().restoration().at(blockX, blockZ) >= history.getRestorationHigh()) {
          greenInsideWar++;
        }
      }
    }

    double share = greenInsideWar / (double) warTorn;

    // "A burned forest beside a surviving grove" has to be a mechanism, not a hope. The restoration
    // pockets are what put living ground inside a war zone; without them this share collapses.
    assertTrue(share > 0.02, "only " + percent(share) + " of war-torn ground keeps any green");
    assertTrue(share < 0.5, percent(share) + " of the war zone is green, which reads as untouched");
  }

  @Test
  void pocketsAreWhatPutGreenInsideWarZones() {
    int broadWouldBeBare = 0;

    for (int x = 0; x < EDGE; x++) {
      for (int z = 0; z < EDGE; z++) {
        int blockX = x * STRIDE;
        int blockZ = z * STRIDE;

        boolean green = engine.maps().restoration().at(blockX, blockZ)
            >= history.getRestorationHigh();
        boolean broadGreen = engine.maps().restoration().broadAt(blockX, blockZ)
            >= history.getRestorationHigh();

        if (green && !broadGreen) {
          broadWouldBeBare++;
        }
      }
    }

    // Proves the pockets do real work: broad noise alone cannot make a small island of life,
    // because a layer wide enough to define a region is far too wide to put anything small inside
    // one.
    assertTrue(broadWouldBeBare > 0, "the restoration pockets never fire");
  }

  @Test
  void ashStillFallsOnEveryStory() {
    Map<RegionStory, Double> lightest = new EnumMap<>(RegionStory.class);

    for (int x = 0; x < 60; x++) {
      for (int z = 0; z < 60; z++) {
        RegionProfile region = engine.atChunk(x * 7 - 200, z * 7 - 200);
        lightest.merge(region.story(), region.profile().ashCarpetCoverage(), Math::min);
      }
    }

    for (Map.Entry<RegionStory, Double> entry : lightest.entrySet()) {
      // The first principle of the engine, now that history is allowed to argue with it: a green
      // refuge may be a light dusting and may never be untouched vanilla ground, because a boundary
      // between edited and pristine land is what always reads as griefing.
      assertTrue(
          entry.getValue() >= HistoryShaping.MIN_CARPET,
          entry.getKey() + " thins ash to " + entry.getValue()
      );
    }
  }

  @Test
  void warDecidesWhereTheBombardmentWasAndGreenKeepsItsGroves() {
    Map<RegionStory, Integer> sampled = new EnumMap<>(RegionStory.class);
    Map<RegionStory, Integer> inZone = new EnumMap<>(RegionStory.class);
    Map<RegionStory, Double> groves = new EnumMap<>(RegionStory.class);

    for (int x = 0; x < 90; x++) {
      for (int z = 0; z < 90; z++) {
        int chunkX = x * 5 - 220;
        int chunkZ = z * 5 - 220;
        RegionProfile region = engine.atChunk(chunkX, chunkZ);

        double impact = engine.fields().impact().sample(chunkX * 16.0 + 8, chunkZ * 16.0 + 8);
        boolean bombarded = impact >= region.profile().impactZoneThreshold();

        sampled.merge(region.story(), 1, Integer::sum);
        inZone.merge(region.story(), bombarded ? 1 : 0, Integer::sum);
        groves.merge(region.story(), region.profile().livingGroveThreshold(), Double::sum);
      }
    }

    int warSamples = sampled.getOrDefault(RegionStory.FRONT_LINE, 0);
    int greenSamples = sampled.getOrDefault(RegionStory.GREEN_REFUGE, 0);
    assertTrue(warSamples > 50 && greenSamples > 50, "not enough of each story to compare");

    double warZones = inZone.get(RegionStory.FRONT_LINE) / (double) warSamples;
    double greenZones = inZone.get(RegionStory.GREEN_REFUGE) / (double) greenSamples;

    // This is the assertion that found the real flaw: with crater density alone left to history,
    // the corruption field — which knows nothing about any war — still decided where the
    // bombardment was.
    assertTrue(
        warZones > 3.0 * greenZones,
        "front lines are bombarded over " + percent(warZones) + " of their ground and refuges over "
            + percent(greenZones) + ", which is not a difference a player would notice"
    );

    double warGroves = groves.get(RegionStory.FRONT_LINE) / warSamples;
    double greenGroves = groves.get(RegionStory.GREEN_REFUGE) / greenSamples;

    assertTrue(
        greenGroves > 2.0 * warGroves,
        "refuges do not keep meaningfully more forest than front lines"
    );
  }

  @Test
  void historyIsDeterministic() {
    HistoryEngine same = new HistoryEngine(SEED, new WorldEngineConfig());
    HistoryEngine other = new HistoryEngine(SEED + 1, new WorldEngineConfig());

    assertEquals(engine.atChunk(31, -17), same.atChunk(31, -17));
    assertNotEquals(engine.atChunk(31, -17), other.atChunk(31, -17));
  }

  @Test
  void influenceOfZeroLeavesTheProfileAlone() {
    CorruptionProfile base = CorruptionProfile.DUSTING;
    HistoryConfig off = HistoryConfigs.silenced();

    // The honest off switch: with every influence at zero the engine has to behave exactly as it
    // did before history existed, whatever the maps say.
    assertEquals(base, HistoryShaping.shape(base, off, 1.0, 1.0, 1.0));
    assertEquals(base, HistoryShaping.shape(base, off, 0.0, 0.0, 0.0));
  }

  private Map<RegionStory, Integer> storyCounts() {
    Map<RegionStory, Integer> counts = new EnumMap<>(RegionStory.class);

    for (int x = 0; x < EDGE; x++) {
      for (int z = 0; z < EDGE; z++) {
        counts.merge(storyAt(x * STRIDE, z * STRIDE), 1, Integer::sum);
      }
    }

    return counts;
  }

  /**
   * Walks several straight lines and returns the average distance the story holds for.
   */
  private double meanStoryRunLength() {
    int step = 24;
    int steps = 2000;
    int runs = 0;
    long distance = 0;

    for (int line = 0; line < 6; line++) {
      int z = line * 4096 - 8192;
      RegionStory previous = null;

      for (int i = 0; i < steps; i++) {
        RegionStory story = storyAt(i * step - 24000, z);
        distance += step;

        if (story != previous) {
          runs++;
          previous = story;
        }
      }
    }

    return distance / (double) runs;
  }

  private RegionStory storyAt(int blockX, int blockZ) {
    return RegionStory.of(
        history,
        engine.maps().war().at(blockX, blockZ),
        engine.maps().ashfall().at(blockX, blockZ),
        engine.maps().restoration().at(blockX, blockZ)
    );
  }

  private static String percent(double share) {
    return Math.round(share * 100.0) + "%";
  }
}
