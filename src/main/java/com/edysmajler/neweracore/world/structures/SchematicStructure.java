package com.edysmajler.neweracore.world.structures;

import com.edysmajler.neweracore.config.StructuresConfig;
import com.edysmajler.neweracore.config.TemplateConfig;
import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.Vegetation;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Palette;
import org.bukkit.structure.Structure;

/**
 * A premade structure loaded from a vanilla {@code .nbt} file.
 *
 * <p>This is the drop-in path: build something in game, save it with a structure block, put the
 * file in {@code plugins/NewEraCore/structures/}, and it joins the scatter on the next restart
 * under its filename as id. Placement rotates it a random quarter turn and places it at slightly
 * under full integrity, so the same file never lands twice exactly alike.
 *
 * <p>Placement goes block by block from the structure's palette rather than through
 * {@code Structure.place}, for one load-bearing reason: a structure file records <em>air</em> as
 * real blocks, so vanilla placement stamps the entire bounding box into the world and carves a
 * clean rectangular void out of the terrain around the model. Skipping air (and structure void)
 * here means the ash, plants, and ground show through everywhere the model has nothing to say —
 * without demanding that every saved build be hand-filled with structure void first. The trade is
 * accepted knowingly: terrain poking through a model's hollow interior on a steep slope is a far
 * smaller artefact than an air box around every wreck.
 *
 * <p>The one piece of care here is the anchor. A structure places from its origin corner and
 * rotates <em>around that corner</em>, so placing every rotation at the same point would swing the
 * building around the site instead of turning it in place. The origin is therefore offset per
 * rotation so the footprint stays centred on the site — get this wrong and three of four rotations
 * stand off-centre by a full building width.
 */
public final class SchematicStructure implements StructureDefinition {

  /** Ground clearance margin folded into the footprint radius. */
  private static final int MARGIN = 8;

  private final String id;
  private final Structure schematic;
  private final double weight;

  /**
   * Wraps one loaded schematic.
   *
   * @param id the structure id, taken from the filename
   * @param schematic the loaded structure
   * @param weight this template's share of the draw
   */
  public SchematicStructure(String id, Structure schematic, double weight) {
    this.id = id;
    this.schematic = schematic;
    this.weight = weight;
  }

  /**
   * Loads every {@code .nbt} file in a plugin's {@code structures/} folder.
   *
   * <p>A file named {@code <name>.crash.nbt} registers as {@code <name>} and gets the full crash
   * treatment on placement — skid trench, impact crater, debris, smoke — with the model seated in
   * the crater. Save crash models with their nose pointing east (+x); the skid comes from the
   * west, behind them. A plain {@code <name>.nbt} is set into the terrain as it is.
   *
   * <p>A file that fails to parse is logged and skipped rather than failing the enable: one broken
   * schematic should not cost the world every other structure.
   *
   * @param plugin the plugin whose data folder holds the files
   * @param config the structures config, whose per-template entries override the filenames
   * @return the loaded definitions, possibly empty
   */
  public static List<StructureDefinition> loadAll(Plugin plugin, StructuresConfig config) {
    File folder = new File(plugin.getDataFolder(), "structures");
    File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".nbt"));

    if (files == null) {
      return List.of();
    }

    List<StructureDefinition> loaded = new ArrayList<>();

    for (File file : files) {
      String name = file.getName().substring(0, file.getName().length() - ".nbt".length())
          .toLowerCase(Locale.ROOT);
      boolean crashByName = name.endsWith(".crash");
      String id = crashByName ? name.substring(0, name.length() - ".crash".length()) : name;

      TemplateConfig template = config.templateFor(id);
      if (!template.isEnabled()) {
        continue;
      }

      try {
        Structure structure = plugin.getServer().getStructureManager().loadStructure(file);
        SchematicStructure model = new SchematicStructure(id, structure, template.getWeight());

        loaded.add(template.isCrash(crashByName)
            ? new CrashedSchematic(model, template.getDestruction())
            : model);
      } catch (IOException e) {
        plugin.getLogger().warning("Skipping unreadable structure " + file.getName()
            + ": " + e.getMessage());
      }
    }

    return loaded;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public int radius() {
    return MARGIN + halfExtent();
  }

  /**
   * Returns how far the model reaches from its centre on the wider horizontal axis.
   */
  int halfExtent() {
    return Math.max(
        (int) Math.ceil(schematic.getSize().getX() / 2.0),
        (int) Math.ceil(schematic.getSize().getZ() / 2.0)
    );
  }

  @Override
  public double weight() {
    return weight;
  }

  @Override
  public void place(StructureField field, StructureSite site) {
    if (field.isFluidColumn(site.centerX(), site.centerZ())) {
      return;
    }

    // Seated so the lowest built layer replaces the ground surface. Anchoring the box instead of
    // the build gets both failure modes: a box saved with empty rows underneath floats its build
    // in the air, and a blind one-block sink buries a low model completely — the ground alongside
    // simply stands over it, which is invisible while placement also carves an air box and obvious
    // the moment it stops.
    stampWithBottomAt(field, site, field.groundY(site.centerX(), site.centerZ()));
  }

  /**
   * Stamps the model with its lowest built layer at a given height.
   *
   * <p>The seam the crash treatment builds on: {@link CrashedSchematic} carves the ground first
   * and then stamps the model onto the crater floor through this, so the plain path and the crash
   * path cannot drift apart in how they read the palette, rotate, or weather the build.
   *
   * @param field the world writer over the footprint
   * @param site the site being built
   * @param bottomY the height the lowest built layer lands on
   */
  void stampWithBottomAt(StructureField field, StructureSite site, int bottomY) {
    List<Palette> palettes = schematic.getPalettes();
    if (palettes.isEmpty()) {
      return;
    }

    StructureRotation rotation = switch (site.rotation()) {
      case 1 -> StructureRotation.CLOCKWISE_90;
      case 2 -> StructureRotation.CLOCKWISE_180;
      case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
      default -> StructureRotation.NONE;
    };

    int sx = (int) schematic.getSize().getX();
    int sy = (int) schematic.getSize().getY();
    int sz = (int) schematic.getSize().getZ();

    int y = bottomY - lowestBuiltLayer(palettes.get(0));

    Location origin = originFor(field, site, rotation, sx, sz, y);

    clearClutterOver(field, origin, rotation, sx, sy, sz);

    // Just under full integrity: a few blocks are missing every time, differently every time
    float integrity = 0.86f + field.random().nextFloat() * 0.12f;

    Palette palette = palettes.get(field.random().nextInt(palettes.size()));

    for (BlockState state : palette.getBlocks()) {
      placeBlock(field, origin, rotation, state, integrity);
    }
  }

  /**
   * Clears the vegetation standing where the model is about to be, and a little above it.
   *
   * <p>Skipping air at placement keeps the terrain, but the terrain includes what grows on it — a
   * model set into a forest otherwise comes out with a tree through its roof. Only vegetation
   * goes: trunks, canopy, plants, snow. The ground itself stays, which is what keeps this from
   * quietly reintroducing the carved-out air box.
   */
  private static void clearClutterOver(
      StructureField field,
      Location origin,
      StructureRotation rotation,
      int sx,
      int sy,
      int sz
  ) {
    for (int localX = 0; localX < sx; localX++) {
      for (int localZ = 0; localZ < sz; localZ++) {
        int rotatedX = switch (rotation) {
          case NONE -> localX;
          case CLOCKWISE_90 -> -localZ;
          case CLOCKWISE_180 -> -localX;
          case COUNTERCLOCKWISE_90 -> localZ;
        };
        int rotatedZ = switch (rotation) {
          case NONE -> localZ;
          case CLOCKWISE_90 -> localX;
          case CLOCKWISE_180 -> -localZ;
          case COUNTERCLOCKWISE_90 -> -localX;
        };

        int x = origin.getBlockX() + rotatedX;
        int z = origin.getBlockZ() + rotatedZ;

        // Reaching above the box takes down the canopy overhanging the model, not just the
        // trunk inside it
        for (int y = origin.getBlockY(); y <= origin.getBlockY() + sy + 6; y++) {
          if (isClutter(field.typeAt(x, y, z))) {
            field.clear(x, y, z);
          }
        }
      }
    }
  }

  /**
   * Returns whether a material is something growing or resting here rather than the ground.
   */
  private static boolean isClutter(Material material) {
    if (material.isAir() || ChunkContext.isFluid(material)) {
      return false;
    }

    return Tag.LOGS.isTagged(material)
        || Tag.LEAVES.isTagged(material)
        || Vegetation.isStanding(material)
        || !material.isSolid();
  }

  /**
   * Returns the lowest local y that actually holds a block, so the seat anchors on the build
   * rather than on the saved bounding box.
   */
  private static int lowestBuiltLayer(Palette palette) {
    int lowest = Integer.MAX_VALUE;

    for (BlockState state : palette.getBlocks()) {
      Material type = state.getType();
      if (!type.isAir() && type != Material.STRUCTURE_VOID) {
        lowest = Math.min(lowest, state.getY());
      }
    }

    return lowest == Integer.MAX_VALUE ? 0 : lowest;
  }

  /**
   * Places one palette block, rotated into position — or leaves the terrain alone.
   */
  private void placeBlock(
      StructureField field,
      Location origin,
      StructureRotation rotation,
      BlockState state,
      float integrity
  ) {
    Material type = state.getType();

    // Air and structure void mean "nothing here": the world underneath stays as the engine left it
    if (type.isAir() || type == Material.STRUCTURE_VOID) {
      return;
    }

    // Containers ride out the integrity roll: weathering may eat a wall block, but eating the
    // loot chest turns a stocked wreck into an empty one at random
    boolean container = state instanceof Container;
    if (!container && field.random().nextFloat() > integrity) {
      return;
    }

    int localX = state.getX();
    int localZ = state.getZ();

    int rotatedX = switch (rotation) {
      case NONE -> localX;
      case CLOCKWISE_90 -> -localZ;
      case CLOCKWISE_180 -> -localX;
      case COUNTERCLOCKWISE_90 -> localZ;
    };
    int rotatedZ = switch (rotation) {
      case NONE -> localZ;
      case CLOCKWISE_90 -> localX;
      case CLOCKWISE_180 -> -localZ;
      case COUNTERCLOCKWISE_90 -> -localX;
    };

    int x = origin.getBlockX() + rotatedX;
    int blockY = origin.getBlockY() + state.getY();
    int z = origin.getBlockZ() + rotatedZ;

    BlockData data = state.getBlockData();
    data.rotate(rotation);

    if (container) {
      // Copied as a block state rather than plain data, so a chest arrives with the contents it
      // was saved with
      BlockState copy = state.copy(new Location(origin.getWorld(), x, blockY, z));
      copy.setBlockData(data);
      copy.update(true, false);
    } else {
      field.set(x, blockY, z, data);
    }
  }

  /**
   * Returns the origin corner that keeps the rotated footprint centred on the site.
   *
   * <p>Rotation maps a local column (x, z) to (-z, x) per clockwise quarter, around the origin, so
   * each rotation's extent sits in a different quadrant and needs its own offset back to centre.
   */
  private Location originFor(
      StructureField field,
      StructureSite site,
      StructureRotation rotation,
      int sx,
      int sz,
      int y
  ) {
    int halfX = (sx - 1) / 2;
    int halfZ = (sz - 1) / 2;

    int offsetX = switch (rotation) {
      case NONE -> -halfX;
      case CLOCKWISE_90 -> halfZ;
      case CLOCKWISE_180 -> halfX;
      case COUNTERCLOCKWISE_90 -> -halfZ;
    };
    int offsetZ = switch (rotation) {
      case NONE -> -halfZ;
      case CLOCKWISE_90 -> -halfX;
      case CLOCKWISE_180 -> halfZ;
      case COUNTERCLOCKWISE_90 -> halfX;
    };

    return new Location(
        field.blockAt(site.centerX(), y, site.centerZ()).getWorld(),
        site.centerX() + offsetX,
        y,
        site.centerZ() + offsetZ
    );
  }
}
