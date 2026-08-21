package com.edysmajler.neweracore.command;

import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.plan.PlannedLocation;
import com.edysmajler.neweracore.plan.WorldPlan;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.plan.PlannedPlacer;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.PlayerCommandExecutor;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Reports what the loaded world plan says, and what will actually be built from it.
 *
 * <p>Every failure mode of a hand-authored plan is silent from inside the game. A plan for the
 * wrong seed is refused, a plan whose file was never found is simply absent, and a location whose
 * type has no builder places nothing — and in all three cases the world looks exactly like a world
 * nobody planned. The log says which happened, but reading a server log to answer "is my plan
 * live" is friction that ends with somebody assuming the feature is broken.
 *
 * <p>So this answers it in game: whether a plan loaded, how much of it can be built, and where the
 * nearest few pieces of it are.
 */
public class Plan implements PlayerCommandExecutor {

  /** How many of the nearest locations to list. */
  private static final int LISTED = 8;

  private final PluginConfig config;
  private final WorldEngine engine;

  /**
   * Creates the executor.
   *
   * @param config the loaded plugin configuration
   * @param engine the running world engine, which owns the plan book
   */
  public Plan(PluginConfig config, WorldEngine engine) {
    this.config = config;
    this.engine = engine;
  }

  @Override
  public void run(Player player, CommandArguments args) {
    final Location standing = player.getLocation();
    WorldPlan plan = engine.plans().forWorld(
        player.getWorld().getName(), player.getWorld().getSeed());

    if (!config.getWorldEngine().getPlan().isEnabled()) {
      line(player, "<yellow>Plans are switched off in the config, so nothing here came from one.");
      return;
    }

    if (plan.locations().isEmpty()) {
      line(player, "<yellow>No plan is loaded for this world. Either none was found in "
          + "<white>plugins/NewEraCore/plans/</white>, or it was refused for being designed "
          + "against another seed — the server log says which.");
      return;
    }

    player.sendRichMessage(config.getMessagePrefix()
        + text("<gray>Plan for seed <white>%d</white>: <white>%d</white> locations, "
            + "<white>%d</white> roads",
        plan.seed(), plan.locations().size(), plan.roads().size()));

    List<PlannedLocation> nearest = plan.locations().stream()
        .sorted(Comparator.comparingDouble(
            location -> location.distanceTo(standing.getBlockX(), standing.getBlockZ())))
        .limit(LISTED)
        .toList();

    for (PlannedLocation location : nearest) {
      report(player, location, standing);
    }

    if (plan.locations().size() > LISTED) {
      line(player, text("<dark_gray>… and %d more", plan.locations().size() - LISTED));
    }
  }

  /**
   * Prints one location, saying plainly whether anything will be built there.
   */
  private void report(Player player, PlannedLocation location, Location standing) {
    int distance = (int) Math.round(
        location.distanceTo(standing.getBlockX(), standing.getBlockZ()));
    String builder = builderFor(location);
    String name = location.name().isBlank() ? location.type().label() : location.name();

    player.sendRichMessage("<gray>"
        + (builder == null ? "<red>x</red>" : "<green>+</green>")
        + " <white>" + name + "</white> at " + TeleportLink.to(location.blockX(), location.blockZ())
        + " <dark_gray>" + distance + "m</dark_gray> "
        + (builder == null
            ? "<red>nothing builds this yet"
            : "<gray>builds as <white>" + builder + "</white>"));
  }

  /**
   * Returns what will build a location, or null when nothing will.
   */
  private String builderFor(PlannedLocation location) {
    if (PlannedPlacer.isSettlement(location.type())) {
      return "a ruined town";
    }

    String structureId = config.getWorldEngine().getPlan().builderFor(location.type());
    if (structureId == null) {
      return null;
    }

    return engine.structures().byId(structureId).isPresent() ? structureId : null;
  }

  private void line(Player player, String message) {
    player.sendRichMessage(config.getMessagePrefix() + message);
  }

  private static String text(String format, Object... values) {
    return String.format(Locale.ROOT, format, values);
  }
}
