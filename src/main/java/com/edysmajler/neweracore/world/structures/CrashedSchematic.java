package com.edysmajler.neweracore.world.structures;

import com.edysmajler.neweracore.world.structures.CrashScars.Frame;
import com.edysmajler.neweracore.world.terrain.LandLookup;
import java.util.Random;

/**
 * A premade model that fell out of the sky: the schematic, plus the mess it made landing.
 *
 * <p>A model simply set into the landscape reads as having always been there — under the trees,
 * under the ash, part of the furniture. A crash is an <em>event</em>, and what says "event" is the
 * ground: the skid gouged in behind, the bowl it stopped in, the burnt ring, the debris, the
 * smoke. So this wraps a {@link SchematicStructure} in the same {@link CrashScars} the code-built
 * fighter jet uses, sizes the crater from the model, and seats the model on the crater floor.
 *
 * <p>Made from a file named {@code <name>.crash.nbt}, or from a config entry that says
 * {@code crash: true}. The skid runs in from behind along the site's rotation, so save crash
 * models with their nose pointing east (+x) — the direction the unrotated skid points.
 *
 * <p>The destruction factor scales the whole mess together — crater, trench, debris, smoke — so a
 * gentle forced landing and a devastating impact come from one number rather than four that can
 * disagree.
 */
public final class CrashedSchematic implements StructureDefinition {

  /** Length of the skid trench behind the crater, at destruction 1. */
  private static final int TRENCH_LENGTH = 22;

  /** How far behind the centre the crash mark can reach, used by the ground test. */
  private static final int TRENCH_REACH = 30;

  /** Crater depth at its centre, at destruction 1. */
  private static final int CRATER_DEPTH = 3;

  /** A trench shorter than this reads as a dent, so below it none is dug at all. */
  private static final int SHORTEST_TRENCH = 6;

  private final SchematicStructure model;
  private final double destruction;

  /**
   * Wraps a schematic in the crash treatment.
   *
   * @param model the model that came down
   * @param destruction how much of a mess the landing makes, 0 (none) to 3 (devastation)
   */
  public CrashedSchematic(SchematicStructure model, double destruction) {
    this.model = model;
    this.destruction = destruction;
  }

  @Override
  public String id() {
    return model.id();
  }

  @Override
  public int radius() {
    return model.radius() + trenchLength() + 6;
  }

  @Override
  public double weight() {
    return model.weight();
  }

  @Override
  public boolean suits(LandLookup land, int blockX, int blockZ) {
    // The trench needs ground behind the crater too, or half the crash mark is out at sea
    return land.isLand(blockX, blockZ)
        && land.isLand(blockX - TRENCH_REACH, blockZ)
        && land.isLand(blockX, blockZ - TRENCH_REACH);
  }

  @Override
  public void place(StructureField field, StructureSite site) {
    // A pond the biome lookup could not see: leave the site empty rather than fill a lake
    if (field.isFluidColumn(site.centerX(), site.centerZ())) {
      return;
    }

    Random random = field.random();

    // The model itself can only turn in quarters, so the skid stays close to its axis — a few
    // degrees off reads as a slew, a large angle reads as a skid that belongs to something else
    double heading = Math.toRadians(site.rotation() * 90.0 + (random.nextDouble() - 0.5) * 24.0);
    Frame frame = new Frame(site.centerX(), site.centerZ(), heading);

    // Everything scales together from the one destruction number, so the parts of the mess can
    // never disagree about how hard the landing was
    int craterDepth = (int) Math.round(CRATER_DEPTH * destruction);
    int craterRadius = model.halfExtent() + 1 + (int) Math.round(2.0 * destruction);
    int trenchLength = trenchLength();

    int ground = field.groundY(site.centerX(), site.centerZ());
    int floorY = ground - craterDepth;

    if (trenchLength >= SHORTEST_TRENCH) {
      CrashScars.carveTrench(
          field, frame, random, -(craterRadius + trenchLength), -(craterRadius - 2));
    }

    if (craterDepth > 0) {
      CrashScars.carveCrater(field, frame, random, craterRadius, craterDepth, floorY);
      model.stampWithBottomAt(field, site, floorY + 1);
    } else {
      // No crater to sit in: seated on the ground like a plain placement
      model.stampWithBottomAt(field, site, ground);
    }

    int pieces = (int) Math.round((12 + random.nextInt(10)) * destruction);
    if (pieces > 0) {
      CrashScars.scatterDebris(field, frame, random, CrashScars.DEBRIS, craterRadius, pieces);
    }

    if (destruction >= 0.25) {
      CrashScars.smoulder(field, frame, random, craterRadius);
    }
  }

  private int trenchLength() {
    return (int) Math.round(TRENCH_LENGTH * destruction);
  }
}
