package com.edysmajler.neweracore.world.roads;

import com.edysmajler.neweracore.world.structures.StructureField;
import com.edysmajler.neweracore.world.structures.loot.LootStocker;
import com.edysmajler.neweracore.world.structures.loot.LootTable;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.block.Container;

/**
 * One ruined house: four walls giving way, a floor going under, and somebody's last cupboard.
 *
 * <p>Ruin is drawn as a <em>gradient</em>, not as random holes. The walls collapse toward one
 * corner — highest at the far corner, rubble at the fallen one — because a building falls apart
 * from its weakest point, and a wall with uniform random bites taken out of it is the griefed
 * pattern in miniature. Roofs are simply gone: twenty years of ash load takes every roof, and a
 * roofless shell reads as time where a holed roof reads as vandals.
 *
 * <p>The door faces the road, which is what makes a row of these a street rather than a scatter
 * of boxes.
 */
public final class RuinedHouse {

  /** Full wall height where the building still stands. */
  private static final int WALL_HEIGHT = 4;

  /** How far below the floor the footings will reach to find ground. */
  private static final int FOOTING_DEPTH = 4;

  private RuinedHouse() {}

  /**
   * Builds one house.
   *
   * @param field the world writer over the town
   * @param centerX absolute block x of the house centre
   * @param centerZ absolute block z of the house centre
   * @param facing quarter turns clockwise; the door wall faces +x at 0, +z at 1
   * @param seed the house's own seed
   * @param loot what the cupboard holds, or null to leave the house bare
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = {"PREDICTABLE_RANDOM", "DMI_RANDOM_USED_ONLY_ONCE"},
      justification = "The seeded java.util.Random is deliberate: a house must build identically "
          + "for its seed, like every placement in this engine."
  )
  public static void build(
      StructureField field,
      int centerX,
      int centerZ,
      int facing,
      long seed,
      LootTable loot
  ) {
    if (field.isFluidColumn(centerX, centerZ)) {
      return;
    }

    Random random = new Random(seed);

    int halfU = 3 + random.nextInt(2);
    int halfV = 3 + random.nextInt(2);
    int floorY = field.groundY(centerX, centerZ);

    boolean stone = random.nextBoolean();

    // The corner the building failed at
    int collapseU = (random.nextBoolean() ? 1 : -1) * halfU;
    int collapseV = (random.nextBoolean() ? 1 : -1) * halfV;
    double maxSpan = Math.hypot(2.0 * halfU, 2.0 * halfV);

    for (int u = -halfU; u <= halfU; u++) {
      for (int v = -halfV; v <= halfV; v++) {
        int x = centerX + rotateX(u, v, facing);
        int z = centerZ + rotateZ(u, v, facing);

        // The house displaced whatever the world had here: slope, plants, drifted ash
        for (int y = floorY + 1; y <= floorY + WALL_HEIGHT + 3; y++) {
          if (!field.typeAt(x, y, z).isAir()) {
            field.clear(x, y, z);
          }
        }

        boolean wall = Math.abs(u) == halfU || Math.abs(v) == halfV;

        if (wall) {
          buildWallColumn(field, random, x, z, u, v, halfU, halfV,
              floorY, collapseU, collapseV, maxSpan, stone);
        } else {
          buildInterior(field, random, x, z, floorY, stone);
        }

        // Footings: on a slope the ground falls away under one side, and a wall standing on air
        // is a floating building, not a ruin
        for (int y = floorY - 1; y >= floorY - FOOTING_DEPTH; y--) {
          if (field.typeAt(x, y, z).isSolid()) {
            break;
          }
          field.set(x, y, z, wallBlock(random, stone));
        }
      }
    }

    dropCupboard(field, random, centerX, centerZ, facing, halfU, halfV, floorY, loot);
  }

  private static void buildWallColumn(
      StructureField field,
      Random random,
      int x,
      int z,
      int u,
      int v,
      int halfU,
      int halfV,
      int floorY,
      int collapseU,
      int collapseV,
      double maxSpan,
      boolean stone
  ) {
    field.set(x, floorY, z, wallBlock(random, stone));

    // The doorway, two wide in the road-facing wall
    if (u == halfU && (v == 0 || v == 1)) {
      return;
    }

    // Highest at the corner farthest from the failure, gone entirely at the failure itself
    double distance = Math.hypot(u - (double) collapseU, v - (double) collapseV) / maxSpan;
    int height = (int) Math.round(WALL_HEIGHT * (0.25 + 0.95 * distance));
    height = Math.min(height, WALL_HEIGHT + (Math.abs(u) == halfU && Math.abs(v) == halfV ? 1 : 0));

    if (random.nextDouble() < 0.3) {
      height--;
    }
    if (random.nextDouble() < 0.06) {
      height = 0;
    }

    // A window every third cell along the wall, never in a corner
    int along = Math.abs(u) == halfU ? v : u;
    boolean corner = Math.abs(u) == halfU && Math.abs(v) == halfV;
    boolean window = !corner && Math.floorMod(along, 3) == 0;

    for (int y = 1; y <= height; y++) {
      if (window && y == 2 && height >= 3) {
        continue;
      }
      field.set(x, floorY + y, z, wallBlock(random, stone));
    }

    // What fell off the wall lies at its foot
    if (height < WALL_HEIGHT - 1 && random.nextDouble() < 0.25) {
      field.set(x, floorY + height + 1, z, stone ? Material.COBBLESTONE : Material.PACKED_MUD);
    }
  }

  private static void buildInterior(
      StructureField field,
      Random random,
      int x,
      int z,
      int floorY,
      boolean stone
  ) {
    double roll = random.nextDouble();

    // Most of the floor survives; where it went, the ground shows through
    if (roll < 0.6) {
      field.set(x, floorY, z, floorBlock(random, stone));
    }

    if (roll > 0.9) {
      field.set(x, floorY + 1, z, Material.COBBLESTONE);
    } else if (random.nextDouble() < 0.05) {
      field.set(x, floorY + 1, z, Material.COBWEB);
    }
  }

  /**
   * Sets the household stores down against a wall, stocked from the town's seed.
   */
  private static void dropCupboard(
      StructureField field,
      Random random,
      int centerX,
      int centerZ,
      int facing,
      int halfU,
      int halfV,
      int floorY,
      LootTable loot
  ) {
    if (loot == null || random.nextDouble() >= 0.55) {
      return;
    }

    // Against the back wall, off-centre
    int u = -halfU + 1;
    int v = random.nextInt(halfV * 2 - 1) - (halfV - 1);
    int x = centerX + rotateX(u, v, facing);
    int z = centerZ + rotateZ(u, v, facing);

    field.set(x, floorY + 1, z,
        random.nextDouble() < 0.6 ? Material.BARREL : Material.CHEST);

    if (field.blockAt(x, floorY + 1, z).getState() instanceof Container container) {
      LootStocker.stock(container.getInventory(), loot, random);
    }
  }

  private static Material wallBlock(Random random, boolean stone) {
    double roll = random.nextDouble();

    if (stone) {
      if (roll < 0.45) {
        return Material.STONE_BRICKS;
      }
      if (roll < 0.70) {
        return Material.CRACKED_STONE_BRICKS;
      }
      return roll < 0.85 ? Material.COBBLESTONE : Material.TUFF;
    }

    if (roll < 0.5) {
      return Material.MUD_BRICKS;
    }
    if (roll < 0.75) {
      return Material.PACKED_MUD;
    }
    return roll < 0.9 ? Material.COBBLESTONE : Material.TUFF;
  }

  private static Material floorBlock(Random random, boolean stone) {
    double roll = random.nextDouble();

    if (stone) {
      return roll < 0.5 ? Material.CRACKED_STONE_BRICKS : Material.TUFF;
    }
    return roll < 0.5 ? Material.PACKED_MUD : Material.COARSE_DIRT;
  }

  private static int rotateX(int u, int v, int facing) {
    return switch (Math.floorMod(facing, 4)) {
      case 1 -> -v;
      case 2 -> -u;
      case 3 -> v;
      default -> u;
    };
  }

  private static int rotateZ(int u, int v, int facing) {
    return switch (Math.floorMod(facing, 4)) {
      case 1 -> u;
      case 2 -> -v;
      case 3 -> -u;
      default -> v;
    };
  }
}
