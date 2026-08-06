package com.edysmajler.neweracore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PluginConfigTest {

  @Test
  void defaultsAreSet() {
    PluginConfig config = new PluginConfig();

    assertEquals("<gray>[<aqua>NewEraCore<gray>]</gray> ", config.getMessagePrefix());
    assertNotNull(config.getWorldEngine());
  }

  @Test
  void worldEngineDefaultsAreSet() {
    WorldEngineConfig engine = new PluginConfig().getWorldEngine();

    assertTrue(engine.isEnabled());
    assertEquals(24, engine.getScanDepth());
    assertEquals(384, engine.getNoise().getCorruptionScale());
  }

  @Test
  void detailScaleIsCoarseEnoughToFormAreas() {
    // Below roughly a dozen blocks the ground materials mix block by block, which reads as confetti
    // rather than as areas of one material. That speckle is the griefed look this engine exists to
    // avoid, so the default has to stay well clear of it.
    assertTrue(new PluginConfig().getWorldEngine().getNoise().getDetailScale() >= 16);
  }

  @Test
  void ashFallsOnEveryLevel() {
    LevelsConfig levels = new PluginConfig().getWorldEngine().getLevels();

    // No level leaves the world vanilla. Half a vanilla world beside swapped patches is the failure
    // mode this design replaced.
    assertTrue(levels.getRecovered().getAshCarpetCoverage() > 0.3);
    assertTrue(levels.getScarred().getAshCarpetCoverage() > 0.7);
    assertEquals(1.0, levels.getDevastated().getAshCarpetCoverage());
  }

  @Test
  void ashDeepensWithEachLevel() {
    LevelsConfig levels = new PluginConfig().getWorldEngine().getLevels();

    assertTrue(levels.getScarred().getDeepAshShare() > levels.getRecovered().getDeepAshShare());
    assertTrue(levels.getDevastated().getDeepAshShare() > levels.getScarred().getDeepAshShare());
    assertTrue(levels.getDevastated().getDriftChance() > levels.getScarred().getDriftChance());

    // Lower scour slope means more of the land is stripped back to rock
    assertTrue(levels.getDevastated().getScourSlope() < levels.getRecovered().getScourSlope());
  }

  @Test
  void forestSurvivalFallsAwayWithEachLevel() {
    LevelsConfig levels = new PluginConfig().getWorldEngine().getLevels();

    assertTrue(levels.getRecovered().getLivingGroveThreshold() >= 0.3,
        "a real share of the forest should survive in recovered land");
    assertTrue(levels.getScarred().getLivingGroveThreshold() < 0.1);
    assertEquals(0.0, levels.getDevastated().getLivingGroveThreshold(),
        "nothing lives at the centre");
  }

  @Test
  void hugeCratersAreRareAndLarge() {
    HugeCraterConfig huge = new PluginConfig().getWorldEngine().getHugeCraters();

    // Far enough apart to stay an event rather than scenery
    assertTrue(huge.getSpacing() >= 512, "huge craters would be too common");
    assertTrue(huge.getRadiusMin() >= 12, "a huge crater must dwarf an ordinary one");
    assertTrue(huge.getRadiusMax() > huge.getRadiusMin());
  }

  @Test
  void hugeCratersExposeMoreOreThanSmallOnes() {
    OreConfig ores = new PluginConfig().getWorldEngine().getOres();

    assertTrue(ores.getHugeCraterChance() > ores.getSmallCraterChance());
    assertTrue(ores.getSmallCraterChance() > 0.0, "small craters should still show something");
    assertTrue(ores.getPreciousShare() < 0.5, "most exposed ore should stay ordinary");
  }

  @Test
  void cratersAreRareOutsideTheWorstLand() {
    LevelsConfig levels = new PluginConfig().getWorldEngine().getLevels();

    assertTrue(levels.getRecovered().getImpactZoneThreshold() > 0.85);
    assertTrue(levels.getDevastated().getImpactZoneThreshold() < 0.5);
    assertTrue(levels.getDevastated().getCratersPerZone()
        > levels.getScarred().getCratersPerZone());
    assertEquals(0.0, levels.getRecovered().getLargeCraterShare());
  }
}
