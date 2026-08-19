package com.edysmajler.neweracore.world.structures;

import java.util.Random;
import org.bukkit.Material;

/**
 * The marks a heavy object leaves when it comes down: the skid, the bowl, the burnt ring, and
 * what tore off on the way in.
 *
 * <p>Shared between the code-built wrecks and the crash-treated schematics, because the ground
 * damage is what sells a crash and it is the same damage whatever fell. The trench hugs each
 * column's own ground — a skid is a mark <em>on</em> the terrain. The crater is the opposite: cut
 * to one reference plane from the impact point's ground, because it is the terrain giving way,
 * and whatever rests in it needs a level floor.
 */
final class CrashScars {

  /** Deepest hollow under a bowl that is filled rather than left as a gap. */
  private static final int MAX_FILL = 5;

  /** Burnt and churned ground. */
  private static final Material[] SCORCH = {
      Material.TUFF, Material.BASALT, Material.COBBLED_DEEPSLATE,
      Material.GRAVEL, Material.TUFF, Material.BLACKSTONE
  };

  /** Torn-off wreckage. */
  static final Material[] DEBRIS = {
      Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.IRON_BARS,
      Material.BLACKSTONE, Material.IRON_BLOCK, Material.LIGHT_GRAY_CONCRETE
  };

  private CrashScars() {}

  /**
   * The crash's own axes: u along the heading, v across it, both in blocks.
   *
   * <p>Forward transforms scatter local points into the world; the inverse turns a world column
   * back into local coordinates so shapes can be tested hole-free.
   *
   * @param centerX absolute block x of the impact point
   * @param centerZ absolute block z of the impact point
   * @param heading the direction of travel, in radians
   */
  record Frame(int centerX, int centerZ, double heading) {

    int worldX(double u, double v) {
      return centerX + (int) Math.round(u * Math.cos(heading) - v * Math.sin(heading));
    }

    int worldZ(double u, double v) {
      return centerZ + (int) Math.round(u * Math.sin(heading) + v * Math.cos(heading));
    }

    int localU(int x, int z) {
      return (int) Math.round(
          (x - centerX) * Math.cos(heading) + (z - centerZ) * Math.sin(heading));
    }

    int localV(int x, int z) {
      return (int) Math.round(
          (z - centerZ) * Math.cos(heading) - (x - centerX) * Math.sin(heading));
    }
  }

  /**
   * Returns one block of burnt, churned ground.
   */
  static Material scorch(Random random) {
    return SCORCH[random.nextInt(SCORCH.length)];
  }

  /**
   * Gouges the skid the object cut before it stopped, deepening towards the impact.
   *
   * <p>Hugs each column's own ground: a skid climbs and drops with the land it scarred.
   *
   * @param fromU local u where the trench begins, the far end
   * @param toU local u where the trench hands over to the crater
   */
  static void carveTrench(StructureField field, Frame frame, Random random, int fromU, int toU) {
    for (int u = fromU; u <= toU; u++) {
      double progress = (u - fromU) / (double) (toU - fromU);
      int depth = (int) Math.round(progress * 2.0);

      for (int v = -3; v <= 3; v++) {
        int x = frame.worldX(u, v);
        int z = frame.worldZ(u, v);

        if (field.isFluidColumn(x, z)) {
          continue;
        }

        int ground = field.groundY(x, z);
        int cut = Math.abs(v) == 3 ? 0 : Math.max(0, depth - (Math.abs(v) == 2 ? 1 : 0));

        for (int i = 0; i < cut; i++) {
          field.clear(x, ground - i, z);
        }

        // The gouge floor and its lips are burnt over, so the mark reads even where it is shallow
        if (cut > 0 || random.nextDouble() < 0.5) {
          int surface = cut > 0 ? ground - cut : ground;
          field.set(x, surface, z, scorch(random));
        }
      }
    }
  }

  /**
   * Cuts the impact bowl to one level floor, clearing whatever stood over it.
   *
   * <p>The floor is measured from the impact point's ground — one plane for the whole crater, so
   * whatever comes to rest in it sits level no matter what each column was doing before. The top
   * of the floor at the centre is exactly {@code floorY}.
   *
   * @param radius the bowl radius in blocks
   * @param depth how deep the bowl cuts at its centre
   * @param floorY the height of the finished floor at the centre
   */
  static void carveCrater(
      StructureField field,
      Frame frame,
      Random random,
      int radius,
      int depth,
      int floorY
  ) {
    for (int du = -radius - 2; du <= radius + 2; du++) {
      for (int dv = -radius - 2; dv <= radius + 2; dv++) {
        double distance = Math.hypot(du, dv);
        int x = frame.worldX(du, dv);
        int z = frame.worldZ(du, dv);

        if (field.isFluidColumn(x, z)) {
          continue;
        }

        int ground = field.groundY(x, z);

        if (distance > radius) {
          // The scorched apron just past the rim, thinning outwards
          if (random.nextDouble() < 1.0 - (distance - radius) / 3.0) {
            field.set(x, ground, z, scorch(random));
          }
          continue;
        }

        double bowl = Math.sqrt(1.0 - (distance / radius) * (distance / radius));
        int floorTop = floorY + depth - (int) Math.round(bowl * depth);

        if (ground > floorTop) {
          // Take the standing world down first, then the ground, so nothing is left floating
          field.clear(x, ground, z);
          for (int y = ground - 1; y > floorTop; y--) {
            field.set(x, y, z, Material.AIR);
          }
        } else if (floorTop - ground > MAX_FILL) {
          // A hollow under the bowl is built up so the crater reads as one shape — but only so
          // far: an unbounded fill pours a plinth, and a gap reads as subsidence where a plinth
          // reads as a bug
          continue;
        } else {
          for (int y = ground + 1; y < floorTop; y++) {
            field.set(x, y, z, scorch(random));
          }
        }

        field.set(x, floorTop, z, scorch(random));
      }
    }
  }

  /**
   * Strews what tore off across the ground around the impact.
   *
   * @param debris the materials wreckage is made of
   * @param fromRadius where the scatter starts, usually the crater rim
   * @param pieces how many pieces to throw
   */
  static void scatterDebris(
      StructureField field,
      Frame frame,
      Random random,
      Material[] debris,
      double fromRadius,
      int pieces
  ) {
    for (int i = 0; i < pieces; i++) {
      double angle = random.nextDouble() * 2.0 * Math.PI;
      double distance = fromRadius + 1 + random.nextDouble() * 12.0;
      int x = frame.centerX() + (int) Math.round(Math.cos(angle) * distance);
      int z = frame.centerZ() + (int) Math.round(Math.sin(angle) * distance);

      if (field.isFluidColumn(x, z)) {
        continue;
      }

      int ground = field.groundY(x, z);
      if (!field.typeAt(x, ground, z).isSolid()) {
        continue;
      }

      field.set(x, ground + 1, z, debris[random.nextInt(debris.length)]);
    }
  }

  /**
   * Leaves a couple of fires smouldering at the rim — the smoke column is how a fresh crash is
   * found from a distance.
   *
   * @param radius the crater radius the fires sit just outside of
   */
  static void smoulder(StructureField field, Frame frame, Random random, int radius) {
    int fires = 1 + random.nextInt(2);

    for (int i = 0; i < fires; i++) {
      double angle = random.nextDouble() * 2.0 * Math.PI;
      double distance = radius + 1 + random.nextInt(3);
      int x = frame.centerX() + (int) Math.round(Math.cos(angle) * distance);
      int z = frame.centerZ() + (int) Math.round(Math.sin(angle) * distance);

      if (field.isFluidColumn(x, z)) {
        continue;
      }

      int ground = field.groundY(x, z);
      if (field.typeAt(x, ground, z).isSolid()
          && field.typeAt(x, ground + 1, z) == Material.AIR) {
        field.set(x, ground + 1, z, Material.CAMPFIRE);
      }
    }
  }
}
