package com.edysmajler.neweracore.command;

import com.edysmajler.neweracore.config.PluginConfig;
import com.edysmajler.neweracore.world.WorldEngine;
import com.edysmajler.neweracore.world.history.HistoryEngine;
import com.edysmajler.neweracore.world.history.Landmark;
import com.edysmajler.neweracore.world.history.LandmarkMap;
import com.edysmajler.neweracore.world.history.LandmarkType;
import com.edysmajler.neweracore.world.infrastructure.Airfield;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.PlayerCommandExecutor;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Finds the nearest place of a given kind.
 *
 * <p>Vanilla has {@code /locate} for its structures, and this world's places need the same thing
 * for the same reason: they are rare on purpose. A missile silo every few thousand blocks is a
 * destination, and a destination nobody can find is just a coordinate the generator knows about.
 *
 * <p>Everything here is arithmetic over the seed, so it can answer about ground that has never been
 * generated and never loads a chunk to do it — which is also why the answer is where the site
 * <em>will</em> be, not where one has been seen.
 */
public class Locate implements PlayerCommandExecutor {

  /** Name of the argument holding what to look for. */
  public static final String TARGET = "target";

  /**
   * How many landmark cells out to search before giving up.
   *
   * <p>Generous, because a type is not evenly spread: each one only belongs in certain regions, so
   * a dam can be genuinely absent from a large stretch of war-torn country. At the default spacing
   * this reaches about eighteen thousand blocks.
   */
  private static final int SEARCH_CELLS = 12;

  private final PluginConfig config;
  private final WorldEngine engine;

  /**
   * Creates the executor.
   *
   * @param config the loaded plugin configuration
   * @param engine the running world engine, which owns each world's history
   */
  public Locate(PluginConfig config, WorldEngine engine) {
    this.config = config;
    this.engine = engine;
  }

  /**
   * Returns the literals this command accepts, in the order they are offered.
   *
   * @return the landmark names, lower case
   */
  public static String[] targets() {
    LandmarkType[] types = LandmarkType.values();
    String[] names = new String[types.length];

    for (int i = 0; i < types.length; i++) {
      names[i] = types[i].name().toLowerCase(Locale.ROOT);
    }

    return names;
  }

  @Override
  public void run(Player player, CommandArguments args) {
    LandmarkType type = parse(String.valueOf(args.get(TARGET)));

    if (type == null) {
      player.sendRichMessage(config.getMessagePrefix()
          + "<gray>Nothing of that kind exists. Try one of: <white>"
          + String.join("<gray>, <white>", targets()));
      return;
    }

    Location location = player.getLocation();
    int blockX = location.getBlockX();
    int blockZ = location.getBlockZ();

    World world = player.getWorld();
    Optional<Landmark> found = search(engine.history(world), type, blockX, blockZ);

    if (found.isEmpty()) {
      player.sendRichMessage(String.format(
          Locale.ROOT,
          "%s<gray>No <white>%s</white> within %d blocks. Each kind only belongs in certain "
              + "country, so some are a long way apart.",
          config.getMessagePrefix(),
          type,
          SEARCH_CELLS * spacing()
      ));
      return;
    }

    report(player, world, found.get(), blockX, blockZ);
  }

  /**
   * Prints where it is, how far, and which way.
   */
  private void report(Player player, World world, Landmark site, int blockX, int blockZ) {
    player.sendRichMessage(String.format(
        Locale.ROOT,
        "%s<gray>Nearest <aqua>%s</aqua><gray>: <white>%d, %d</white>  %d blocks %s",
        config.getMessagePrefix(),
        site.type(),
        site.centerX(),
        site.centerZ(),
        Math.round(site.distanceTo(blockX, blockZ)),
        Bearing.of(site.centerX() - (double) blockX, site.centerZ() - (double) blockZ)
    ));

    player.sendRichMessage(String.format(
        Locale.ROOT,
        "<gray>  %s <dark_gray>%s",
        engine.history(world).at(site.centerX(), site.centerZ()).story(),
        "site radius " + site.radius()
    ));

    describeRunway(player, world, site);
  }

  /**
   * Adds the runway line, for the one landmark that has something built on it already.
   */
  private void describeRunway(Player player, World world, Landmark site) {
    Airfield airfield = Airfield.at(
        site,
        config.getWorldEngine().getInfrastructure(),
        world.getSeaLevel(),
        world.getSeed()
    );

    if (airfield == null) {
      return;
    }

    player.sendRichMessage(String.format(
        Locale.ROOT,
        "<gray>  runway <white>%d x %d</white> at <white>y=%d</white>, bearing <white>%.0f°",
        airfield.length(),
        airfield.width(),
        airfield.platformY(),
        (Math.toDegrees(Math.atan2(airfield.sin(), airfield.cos())) + 360.0) % 360.0
    ));
  }

  /**
   * Walks outward a ring of cells at a time until something of the right kind turns up.
   *
   * <p>One ring further than the first hit, because a site found in the corner of a ring can be
   * further away than one sitting just inside the next — searching outward is not the same as
   * searching nearest-first.
   */
  private Optional<Landmark> search(
      HistoryEngine history,
      LandmarkType type,
      int blockX,
      int blockZ
  ) {
    LandmarkMap landmarks = history.landmarks();
    int spacing = spacing();
    int cellX = Math.floorDiv(blockX, spacing);
    int cellZ = Math.floorDiv(blockZ, spacing);

    Landmark best = null;
    double nearest = Double.MAX_VALUE;
    int foundAt = Integer.MAX_VALUE;

    for (int ring = 0; ring <= SEARCH_CELLS; ring++) {
      if (ring > foundAt) {
        break;
      }

      for (int dx = -ring; dx <= ring; dx++) {
        for (int dz = -ring; dz <= ring; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
            continue;
          }

          Optional<Landmark> site = landmarks.siteIn(cellX + dx, cellZ + dz);
          if (site.isEmpty() || site.get().type() != type) {
            continue;
          }

          double distance = site.get().distanceTo(blockX, blockZ);
          if (distance < nearest) {
            nearest = distance;
            best = site.get();
            foundAt = ring;
          }
        }
      }
    }

    return Optional.ofNullable(best);
  }

  private int spacing() {
    return config.getWorldEngine().getHistory().getLandmarks().getSpacing();
  }

  private static LandmarkType parse(String name) {
    for (LandmarkType type : LandmarkType.values()) {
      if (type.name().equalsIgnoreCase(name)) {
        return type;
      }
    }

    return null;
  }
}
