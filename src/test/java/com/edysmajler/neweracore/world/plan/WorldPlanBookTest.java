package com.edysmajler.neweracore.world.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.config.PlanConfig;
import com.edysmajler.neweracore.plan.LocationType;
import com.edysmajler.neweracore.plan.PlannedLocation;
import com.edysmajler.neweracore.plan.WorldPlan;
import com.edysmajler.neweracore.plan.WorldPlanFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldPlanBookTest {

  private static final long SEED = 7153137573198281821L;

  private static WorldPlan planFor(long seed) {
    return new WorldPlan(seed, -1500, -1500, 3000,
        List.of(new PlannedLocation("haven", LocationType.TOWN, "Haven", 482, -317, 180, "")),
        List.of());
  }

  private static WorldPlanBook book(Path folder) {
    return new WorldPlanBook(folder, new PlanConfig(), Logger.getAnonymousLogger());
  }

  private static void write(Path folder, String name, WorldPlan plan) throws IOException {
    WorldPlanFile.write(plan, folder.resolve(WorldPlanBook.FOLDER).resolve(name));
  }

  @Test
  @DisplayName("a plan named after the world is preferred over the shared one")
  void perWorldWins(@TempDir Path folder) throws IOException {
    write(folder, "world.json", planFor(SEED));
    write(folder, WorldPlanBook.SHARED_FILE,
        new WorldPlan(SEED, 0, 0, 512, List.of(), List.of()));

    assertEquals(1, book(folder).forWorld("world", SEED).locations().size());
  }

  @Test
  @DisplayName("the shared plan is the fallback")
  void sharedIsTheFallback(@TempDir Path folder) throws IOException {
    write(folder, WorldPlanBook.SHARED_FILE, planFor(SEED));

    assertEquals(1, book(folder).forWorld("anything", SEED).locations().size());
  }

  @Test
  @DisplayName("a plan for another seed is refused rather than applied to different ground")
  void wrongSeedIsRefused(@TempDir Path folder) throws IOException {
    write(folder, WorldPlanBook.SHARED_FILE, planFor(SEED));

    // The silent-failure case this guard exists for: the file is perfectly valid, and applying it
    // would put every town on terrain nobody looked at
    assertTrue(book(folder).forWorld("world", SEED + 1).locations().isEmpty());
    assertFalse(book(folder).hasPlan("world", SEED + 1));
  }

  @Test
  @DisplayName("no plan file means an empty plan, not a failure")
  void missingPlanIsEmpty(@TempDir Path folder) {
    assertTrue(book(folder).forWorld("world", SEED).locations().isEmpty());
  }

  @Test
  @DisplayName("an unreadable plan costs the plan, not the world")
  void brokenPlanIsEmpty(@TempDir Path folder) throws IOException {
    Path plans = folder.resolve(WorldPlanBook.FOLDER);
    Files.createDirectories(plans);
    Files.writeString(plans.resolve(WorldPlanBook.SHARED_FILE), "{ this is not json");

    assertTrue(book(folder).forWorld("world", SEED).locations().isEmpty());
  }

  @Test
  @DisplayName("the file is read once and kept, not re-read per chunk")
  void planIsCached(@TempDir Path folder) throws IOException {
    write(folder, WorldPlanBook.SHARED_FILE, planFor(SEED));
    WorldPlanBook book = book(folder);

    WorldPlan first = book.forWorld("world", SEED);
    Files.delete(folder.resolve(WorldPlanBook.FOLDER).resolve(WorldPlanBook.SHARED_FILE));

    // Every chunk asks; the answer must not depend on the file still being there
    assertEquals(first, book.forWorld("world", SEED));
  }

  @Test
  @DisplayName("plans switched off in the config are not read at all")
  void disabledReadsNothing(@TempDir Path folder) throws IOException {
    write(folder, WorldPlanBook.SHARED_FILE, planFor(SEED));

    PlanConfig off = new PlanConfig() {
      @Override
      public boolean isEnabled() {
        return false;
      }
    };

    assertTrue(new WorldPlanBook(folder, off, Logger.getAnonymousLogger())
        .forWorld("world", SEED).locations().isEmpty());
  }
}
