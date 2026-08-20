package com.edysmajler.neweracore.world.roads;

import com.edysmajler.neweracore.world.structures.StructureField;
import com.edysmajler.neweracore.world.structures.loot.LootStocker;
import com.edysmajler.neweracore.world.structures.loot.LootTable;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.block.Chest;

/**
 * One abandoned car, drawn whole on the road it stopped on.
 *
 * <p>Built procedurally like the fighter jet, and for the same reason: a car park where every
 * wreck faces one of four ways reads as placed, not abandoned, so the body is rasterised by
 * inverse-transforming world columns into the car's own axes — hole-free at any heading. Three
 * silhouettes (sedan, van, pickup) and a burnt-out state vary from the site seed; the shapes stay
 * whole, because a variant is a different car, never a car with holes punched in it.
 *
 * <p>Some cars keep a boot chest, stocked from the civilian table off the same seeded stream as
 * everything else about the car.
 */
public final class CarWreck {

  /** Covers the body's furthest corner at any heading. */
  private static final int REACH = 4;

  /** Weathered paint, muted to fit twenty years of ash. */
  private static final Material[] PAINT = {
      Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.WHITE_CONCRETE,
      Material.RED_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.BROWN_TERRACOTTA
  };

  private CarWreck() {}

  /**
   * Draws one car.
   *
   * @param field the world writer over the car's footprint
   * @param site the car being placed
   * @param loot what the boot holds, or null for an empty one
   */
  public static void place(StructureField field, CarSite site, LootTable loot) {
    if (field.isFluidColumn(site.x(), site.z())) {
      return;
    }

    Random random = field.random();

    double bodyRoll = random.nextDouble();
    // Sedan, van, or pickup: where the cabin sits along the body
    int cabinFrom = bodyRoll < 0.55 ? -1 : (bodyRoll < 0.8 ? -2 : 0);
    int cabinTo = bodyRoll < 0.55 ? 0 : 1;

    boolean burnt = random.nextDouble() < 0.25;
    boolean hasBoot = random.nextDouble() < 0.3;
    Material paint = PAINT[random.nextInt(PAINT.length)];

    int baseY = field.groundY(site.x(), site.z());
    double cos = Math.cos(site.heading());
    double sin = Math.sin(site.heading());

    boolean bootPlaced = false;

    for (int dx = -REACH; dx <= REACH; dx++) {
      for (int dz = -REACH; dz <= REACH; dz++) {
        int u = (int) Math.round(dx * cos + dz * sin);
        int v = (int) Math.round(-dx * sin + dz * cos);

        if (u < -2 || u > 2 || Math.abs(v) > 1) {
          continue;
        }

        int x = site.x() + dx;
        int z = site.z() + dz;

        // The car displaced whatever settled here: plants, drifted ash, snow
        for (int y = baseY + 1; y <= baseY + 4; y++) {
          if (!field.typeAt(x, y, z).isAir()) {
            field.clear(x, y, z);
          }
        }

        boolean wheel = Math.abs(u) == 2 && Math.abs(v) == 1;
        Material base = wheel ? Material.BLACKSTONE : body(random, paint, burnt);

        if (hasBoot && !bootPlaced && u == -2 && v == 0) {
          field.set(x, baseY + 1, z, Material.CHEST);
          bootPlaced = true;

          if (loot != null
              && field.blockAt(x, baseY + 1, z).getState() instanceof Chest chest) {
            LootStocker.stock(chest.getBlockInventory(), loot, random);
          }
        } else {
          field.set(x, baseY + 1, z, base);
        }

        if (u >= cabinFrom && u <= cabinTo) {
          if (v == 0) {
            field.set(x, baseY + 2, z, body(random, paint, burnt));
          } else if (!burnt && random.nextDouble() > 0.3) {
            // Side windows; the missing ones shattered years ago, all of them if it burned
            field.set(x, baseY + 2, z, Material.GRAY_STAINED_GLASS);
          }
        }
      }
    }
  }

  private static Material body(Random random, Material paint, boolean burnt) {
    double roll = random.nextDouble();

    if (burnt) {
      // Charred through, with the odd patch of paint that survived
      if (roll < 0.6) {
        return Material.BLACKSTONE;
      }
      return roll < 0.85 ? Material.COAL_BLOCK : paint;
    }

    // A speckle of rust on otherwise surviving paint
    return roll < 0.12 ? Material.EXPOSED_CUT_COPPER : paint;
  }
}
