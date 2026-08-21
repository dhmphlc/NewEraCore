package com.edysmajler.neweracore.world.plan;

import com.edysmajler.neweracore.config.PlanConfig;
import com.edysmajler.neweracore.plan.WorldPlan;
import com.edysmajler.neweracore.plan.WorldPlanFile;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds each world's plan and keeps it, so the generator reads the file once rather than per chunk.
 *
 * <p>A plan is looked up by convention: {@code plans/<world>.json} if it is there, otherwise the
 * shared {@code plans/world-plan.json}. Per-world first because a server usually has one designed
 * world and several it does not care about, and the shared file as a fallback because naming the
 * world in the filename is a step nobody remembers while testing.
 *
 * <p><strong>The seed guard is the important part.</strong> Every coordinate in a plan was
 * chosen by looking at the terrain one seed produces. Applied to another seed the file is still
 * perfectly valid — the towns would just be underwater, on cliffs, or inside each other, with
 * nothing in the log to say why. So a plan whose seed does not match the world is refused outright
 * and said loudly, because the alternative is a world that looks designed by somebody who was not
 * paying attention.
 */
public final class WorldPlanBook {

  /** The folder plans live in, under the plugin's data folder. */
  public static final String FOLDER = "plans";

  /** The plan every world falls back to when none is named for it. */
  public static final String SHARED_FILE = "world-plan.json";

  private static final WorldPlan NOTHING = new WorldPlan(0L, 0, 0, 0, List.of(), List.of());

  private final Path dataFolder;
  private final PlanConfig config;
  private final Logger logger;
  private final Map<String, WorldPlan> byWorld = new ConcurrentHashMap<>();

  /**
   * Creates the book.
   *
   * @param dataFolder the plugin's data folder, which plans are resolved against
   * @param config the plan settings
   * @param logger the logger that reports what was loaded, or why nothing was
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The plugin's logger is shared by design, and a Path is immutable."
  )
  public WorldPlanBook(Path dataFolder, PlanConfig config, Logger logger) {
    this.dataFolder = dataFolder;
    this.config = config;
    this.logger = logger;
  }

  /**
   * Returns a world's plan, loading it on first use.
   *
   * <p>Always answers a plan; a world with no plan, a plan that will not parse, and a plan for
   * another seed all answer an empty one, because generation must continue either way. The
   * difference between those cases lives in the log, which is written once per world rather than
   * once per chunk.
   *
   * @param worldName the world's name
   * @param seed the world's seed
   * @return the plan, empty when there is none to apply
   */
  public WorldPlan forWorld(String worldName, long seed) {
    return byWorld.computeIfAbsent(worldName, name -> load(name, seed));
  }

  /**
   * Returns whether any plan has been loaded for a world.
   *
   * @param worldName the world's name
   * @param seed the world's seed
   * @return true when the plan holds at least one location
   */
  public boolean hasPlan(String worldName, long seed) {
    return !forWorld(worldName, seed).locations().isEmpty();
  }

  private WorldPlan load(String worldName, long seed) {
    if (!config.isEnabled()) {
      return NOTHING;
    }

    Path file = fileFor(worldName);
    if (file == null) {
      logger.info(() -> "No world plan for " + worldName + ": looked for "
          + FOLDER + "/" + worldName + ".json and " + FOLDER + "/" + SHARED_FILE);
      return NOTHING;
    }

    WorldPlan plan;
    try {
      plan = WorldPlanFile.read(file);
    } catch (IOException e) {
      // Forgiving like the schematic and loot loaders: a broken plan costs the plan, not the world
      logger.log(Level.SEVERE, "Could not read world plan " + file + "; generating without it", e);
      return NOTHING;
    }

    if (plan.seed() != seed) {
      logger.severe(() -> "Refusing the world plan " + file + ": it was designed against seed "
          + plan.seed() + " but " + worldName + " is seed " + seed
          + ". Every position in it was chosen by looking at different terrain.");
      return NOTHING;
    }

    logger.info(() -> "Loaded world plan " + file.getFileName() + " for " + worldName + ": "
        + plan.locations().size() + " locations, " + plan.roads().size() + " roads");
    return plan;
  }

  private Path fileFor(String worldName) {
    if (!config.getFile().isBlank()) {
      Path named = dataFolder.resolve(config.getFile());
      return Files.isRegularFile(named) ? named : null;
    }

    Path perWorld = dataFolder.resolve(FOLDER).resolve(worldName + ".json");
    if (Files.isRegularFile(perWorld)) {
      return perWorld;
    }

    Path shared = dataFolder.resolve(FOLDER).resolve(SHARED_FILE);
    return Files.isRegularFile(shared) ? shared : null;
  }
}
