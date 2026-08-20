package com.edysmajler.neweracore.world.structures;

import com.edysmajler.neweracore.world.structures.CrashScars.Frame;
import com.edysmajler.neweracore.world.structures.loot.LootStocker;
import com.edysmajler.neweracore.world.structures.loot.LootTable;
import com.edysmajler.neweracore.world.structures.loot.LootTables;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.block.Chest;

/**
 * A crashed fighter jet: skid trench, impact crater, the airframe, debris, and the pilot's kit.
 *
 * <p>Built procedurally rather than from a schematic so the crash can point any way at all — a
 * wreck that only ever faces the four cardinal directions reads as placed, not fallen. The hull is
 * rasterised by walking the world columns of the bounding box and asking, for each, where it sits
 * in the jet's own axes; asking the question that way round leaves no rounding holes at shallow
 * angles.
 *
 * <p>The ground damage — trench, crater, debris — is {@link CrashScars}, shared with every
 * crash-treated schematic, because the mess is what sells a crash and it is the same mess whatever
 * fell.
 *
 * <p>Everything varies from the site seed: the exact heading inside the rotation quarter, a snapped
 * wing thrown clear, a broken spine, how much debris burned. The shapes stay whole — a variant is a
 * different silhouette, never a silhouette with holes in it.
 */
public final class FighterJet implements StructureDefinition {

  /** Everything the jet can reach: nose, trench tail, thrown wing, debris. */
  private static final int RADIUS = 40;

  /** Crater radius around the impact point. */
  private static final int CRATER_RADIUS = 9;

  /** Crater depth at its centre. */
  private static final int CRATER_DEPTH = 3;

  /** Local u where the trench begins (far end) and ends (crater rim). */
  private static final int TRENCH_FROM = -32;
  private static final int TRENCH_TO = -10;

  private final double weight;
  private final LootTable loot;

  /** Creates the jet with an equal share of the draw and the standard military kit. */
  public FighterJet() {
    this(1.0, LootTables.builtIn(LootTables.MILITARY));
  }

  /**
   * Creates the jet with a configured share of the draw and loadout.
   *
   * @param weight this structure's share when a site picks what stands on it
   * @param loot what the pilot's kit holds, or null for an empty chest
   */
  public FighterJet(double weight, LootTable loot) {
    this.weight = weight;
    this.loot = loot;
  }

  @Override
  public String id() {
    return "fighter_jet";
  }

  @Override
  public int radius() {
    return RADIUS;
  }

  @Override
  public double weight() {
    return weight;
  }

  @Override
  public boolean suits(LandLookup land, int blockX, int blockZ) {
    // The trench needs ground behind the crater too, or half the crash mark is out at sea
    return land.isLand(blockX, blockZ)
        && land.isLand(blockX + TRENCH_FROM, blockZ)
        && land.isLand(blockX, blockZ + TRENCH_FROM);
  }

  @Override
  public void place(StructureField field, StructureSite site) {
    // A pond the biome lookup could not see: leave the site empty rather than fill a lake
    if (field.isFluidColumn(site.centerX(), site.centerZ())) {
      return;
    }

    Random random = field.random();

    // The heading wanders inside its rotation quarter, so no two crashes point quite the same way
    double heading = Math.toRadians(site.rotation() * 90.0 + (random.nextDouble() - 0.5) * 64.0);
    Frame frame = new Frame(site.centerX(), site.centerZ(), heading);

    boolean wingSnapped = random.nextDouble() < 0.35;
    boolean tailBroken = random.nextDouble() < 0.3;
    boolean spineBroken = random.nextDouble() < 0.25;

    int craterGround = field.groundY(site.centerX(), site.centerZ());
    int floorY = craterGround - CRATER_DEPTH;

    CrashScars.carveTrench(field, frame, random, TRENCH_FROM, TRENCH_TO);
    CrashScars.carveCrater(field, frame, random, CRATER_RADIUS, CRATER_DEPTH, floorY);
    buildAirframe(field, frame, random, floorY + 1, wingSnapped, tailBroken, spineBroken);

    if (wingSnapped) {
      throwWing(field, frame, random);
    }

    CrashScars.scatterDebris(
        field, frame, random, CrashScars.DEBRIS, CRATER_RADIUS, 18 + random.nextInt(12));
    dropSurvivalKit(field, frame, random, floorY + 1);
  }

  /**
   * Draws the airframe resting on the crater floor, nose past the impact point.
   *
   * <p>Walks the bounding box in world columns and inverse-transforms each into the jet's own
   * axes, so the shape is hole-free at any heading.
   */
  private void buildAirframe(
      StructureField field,
      Frame frame,
      Random random,
      int baseY,
      boolean wingSnapped,
      boolean tailBroken,
      boolean spineBroken
  ) {
    // Covers the airframe's furthest corner (u -13, v 9) at any heading
    int reach = 17;

    for (int dx = -reach; dx <= reach; dx++) {
      for (int dz = -reach; dz <= reach; dz++) {
        int x = frame.centerX() + dx;
        int z = frame.centerZ() + dz;
        int u = frame.localU(x, z);
        int v = frame.localV(x, z);

        boolean fuselage = u >= -13 && u <= 10 && Math.abs(v) <= fuselageHalfWidth(u);
        boolean wing = isWing(u, v, wingSnapped);
        boolean stabilizer = u >= -12 && u <= -10 && Math.abs(v) > 1 && Math.abs(v) <= 4;

        if (!fuselage && !wing && !stabilizer) {
          continue;
        }

        // The airframe cut its own way in: whatever the terrain still holds over the wreck goes
        clearOver(field, x, baseY, z);

        if (spineBroken && u >= -2 && u <= 0 && fuselage && !wing) {
          // The break in the spine: charred stubs at floor level instead of hull
          if (random.nextDouble() < 0.5) {
            field.set(x, baseY, z, Material.BLACKSTONE);
          }
          continue;
        }

        if (fuselage) {
          drawFuselageColumn(field, random, x, z, u, v, baseY, tailBroken);
        } else {
          // Wings and stabilizers are one panel thick, riding at hull height
          field.set(x, baseY + 1, z, hull(random));
        }
      }
    }
  }

  private void drawFuselageColumn(
      StructureField field,
      Random random,
      int x,
      int z,
      int u,
      int v,
      int baseY,
      boolean tailBroken
  ) {
    if (u <= -12) {
      // Engine nozzles, burnt out
      field.set(x, baseY, z, Material.BLACKSTONE);
      field.set(x, baseY + 1, z, Material.BLACKSTONE);
      return;
    }

    // Two blocks of hull everywhere; the nose dips into the ground it ploughed
    int lift = u >= 8 ? -1 : 0;
    field.set(x, baseY + lift, z, hull(random));
    field.set(x, baseY + 1 + lift, z, hull(random));

    if (v == 0 && u >= 4 && u <= 7) {
      // Canopy glass over the cockpit
      field.set(x, baseY + 2, z, Material.GRAY_STAINED_GLASS);
    } else if (v == 0 && u >= -8 && u <= 3) {
      // The spine
      field.set(x, baseY + 2, z, hull(random));
    }

    if (v == 0 && u <= -9) {
      // The tail fin, higher towards the rear — or its snapped stub
      int height = tailBroken ? 1 : -u - 8;
      for (int i = 1; i <= height; i++) {
        field.set(x, baseY + 1 + i, z, hull(random));
      }
    }

    if (v == 0 && u == -10) {
      // The engine still smoulders: the smoke column is how a wreck is found from a distance
      field.set(x, baseY + 2 + (tailBroken ? 1 : 2), z, Material.CAMPFIRE);
    }
  }

  private static int fuselageHalfWidth(int u) {
    if (u >= 8) {
      return 0;
    }
    if (u >= -6) {
      return 1;
    }
    return u >= -13 ? 1 : 0;
  }

  private static boolean isWing(int u, int v, boolean wingSnapped) {
    if (u < -7 || u > 1) {
      return false;
    }

    // Swept delta: the half-span grows towards the trailing edge
    int halfSpan = 2 + (int) Math.round((1 - u) * 7.0 / 8.0);
    int span = Math.min(halfSpan, 9);

    if (Math.abs(v) <= 1 || Math.abs(v) > span) {
      return false;
    }

    // A snapped starboard wing ends in a stub; the rest of it lies out in the debris field
    return !(wingSnapped && v > 3);
  }

  /**
   * Drops the snapped wing where it landed: a whole panel, clear of the airframe.
   */
  private void throwWing(StructureField field, Frame frame, Random random) {
    int wingU = -16 - random.nextInt(6);
    int wingV = 7 + random.nextInt(4);

    for (int du = 0; du < 6; du++) {
      for (int dv = 0; dv < 3 + (du < 3 ? 1 : 0); dv++) {
        int x = frame.worldX(wingU + du, wingV + dv);
        int z = frame.worldZ(wingU + du, wingV + dv);

        if (field.isFluidColumn(x, z)) {
          continue;
        }

        int ground = field.groundY(x, z);
        field.clear(x, ground + 1, z);
        field.set(x, ground + 1, z, hull(random));

        if (random.nextDouble() < 0.3) {
          field.set(x, ground, z, CrashScars.scorch(random));
        }
      }
    }
  }

  /**
   * Sets the pilot's kit down in the crater beside the hull, stocked from the site seed.
   */
  private void dropSurvivalKit(StructureField field, Frame frame, Random random, int baseY) {
    int x = frame.worldX(3, 3);
    int z = frame.worldZ(3, 3);

    field.set(x, baseY, z, Material.CHEST);

    if (loot != null && field.blockAt(x, baseY, z).getState() instanceof Chest chest) {
      LootStocker.stock(chest.getBlockInventory(), loot, random);
    }
  }

  /**
   * Clears the terrain still standing over an airframe column.
   */
  private static void clearOver(StructureField field, int x, int baseY, int z) {
    for (int y = baseY; y <= baseY + 6; y++) {
      if (!field.typeAt(x, y, z).isAir()) {
        field.clear(x, y, z);
      }
    }
  }

  private static Material hull(Random random) {
    double roll = random.nextDouble();
    if (roll < 0.65) {
      return Material.LIGHT_GRAY_CONCRETE;
    }
    return roll < 0.9 ? Material.GRAY_CONCRETE : Material.BLACKSTONE;
  }
}
