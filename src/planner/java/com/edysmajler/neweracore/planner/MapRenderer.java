package com.edysmajler.neweracore.planner;

import com.edysmajler.neweracore.plan.TerrainClass;
import com.edysmajler.neweracore.plan.WorldSnapshot;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Set;

/**
 * Turns a snapshot into a picture, one pixel per sample.
 *
 * <p>Shaded relief rather than a flat height ramp, because a ramp cannot show a valley. Two
 * positions at the same height look identical under a ramp whether one is a floodplain and the
 * other a shelf halfway up a mountain; lighting the surface from the north-west makes the shape of
 * the land visible, which is the entire reason to look at a map before siting anything.
 *
 * <p>Renders at sample resolution and lets the view scale it. A three-kilometre world at four
 * blocks per sample is 750 pixels square — small enough to rebuild in a few milliseconds whenever
 * a toggle changes, which keeps overlay switching instant instead of progressive.
 */
public final class MapRenderer {

  /** Ground tint under everything, before shading. */
  private static final Color BARE = new Color(0x8A8378);

  /** How much the north-west light darkens or lightens a slope, per block of difference. */
  private static final double SHADE_PER_BLOCK = 0.055;

  /** Cap on the shading, so a cliff face does not go pure black or pure white. */
  private static final double SHADE_LIMIT = 0.45;

  private final WorldSnapshot snapshot;
  private final int lowest;
  private final int highest;

  /**
   * Creates a renderer over one snapshot, measuring its height range once.
   *
   * @param snapshot the exported terrain
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The snapshot is read-only once loaded and the plan is the one "
          + "document every panel edits: sharing both is the design, and copying either "
          + "would put the panels out of step with each other."
  )
  public MapRenderer(WorldSnapshot snapshot) {
    this.snapshot = snapshot;

    int low = Integer.MAX_VALUE;
    int high = Integer.MIN_VALUE;
    int edge = snapshot.samplesPerSide();

    for (int x = 0; x < edge; x++) {
      for (int z = 0; z < edge; z++) {
        int height = snapshot.heightOfSample(x, z);
        if (height == WorldSnapshot.UNKNOWN_HEIGHT) {
          continue;
        }
        low = Math.min(low, height);
        high = Math.max(high, height);
      }
    }

    // A snapshot with nothing sampled would otherwise divide by zero when normalising heights
    this.lowest = low == Integer.MAX_VALUE ? 0 : low;
    this.highest = high == Integer.MIN_VALUE ? lowest + 1 : Math.max(high, low + 1);
  }

  /**
   * Returns the lowest sampled height, for a legend.
   *
   * @return the height in blocks
   */
  public int lowest() {
    return lowest;
  }

  /**
   * Returns the highest sampled height.
   *
   * @return the height in blocks
   */
  public int highest() {
    return highest;
  }

  /**
   * Draws the map.
   *
   * @param overlays which raster overlays are switched on
   * @return an image one pixel per sample, its top-left corner the snapshot's lowest corner
   */
  public BufferedImage render(Set<Overlay> overlays) {
    int edge = snapshot.samplesPerSide();
    BufferedImage image = new BufferedImage(edge, edge, BufferedImage.TYPE_INT_RGB);
    boolean tintTerrain = overlays.contains(Overlay.TERRAIN);
    boolean showWater = overlays.contains(Overlay.WATER);
    boolean showCorruption = overlays.contains(Overlay.CORRUPTION);
    boolean showImpact = overlays.contains(Overlay.IMPACT);

    for (int z = 0; z < edge; z++) {
      for (int x = 0; x < edge; x++) {
        image.setRGB(x, z, colourOf(x, z, tintTerrain, showWater, showCorruption, showImpact));
      }
    }

    return image;
  }

  private int colourOf(
      int x,
      int z,
      boolean tintTerrain,
      boolean showWater,
      boolean showCorruption,
      boolean showImpact
  ) {
    int height = snapshot.heightOfSample(x, z);
    if (height == WorldSnapshot.UNKNOWN_HEIGHT) {
      return 0x101214;
    }

    TerrainClass terrain = snapshot.terrainOfSample(x, z);
    int water = snapshot.waterOfSample(x, z);

    Color base = tintTerrain ? tintOf(terrain) : elevationOf(height);
    Color shaded = shade(base, x, z, height);

    if (showWater && (water > 0 || terrain.isWater())) {
      shaded = blend(shaded, depthColour(water), 0.75);
    }

    if (showCorruption) {
      double value = snapshot.corruptionOfSample(x, z);
      shaded = blend(shaded, new Color(0xC0, 0x39, 0x2B), 0.45 * value * value);
    }

    if (showImpact) {
      double value = snapshot.impactOfSample(x, z);
      // Squared, and only the top of the field: the impact map is only interesting where it is
      // high enough for craters to actually appear, and a linear wash hides that.
      double weight = value < 0.6 ? 0.0 : (value - 0.6) / 0.4;
      shaded = blend(shaded, new Color(0xF2, 0x8C, 0x28), 0.6 * weight * weight);
    }

    return shaded.getRGB();
  }

  /**
   * Returns the unlit ground colour for a height, ramped low green to high grey.
   */
  private Color elevationOf(int height) {
    double unit = Math.clamp((height - (double) lowest) / (highest - lowest), 0.0, 1.0);

    if (unit < 0.45) {
      return lerp(new Color(0x4E6B3C), new Color(0x8F8A4E), unit / 0.45);
    }
    if (unit < 0.8) {
      return lerp(new Color(0x8F8A4E), new Color(0x8A6B4A), (unit - 0.45) / 0.35);
    }

    return lerp(new Color(0x8A6B4A), new Color(0xD8D5D0), (unit - 0.8) / 0.2);
  }

  /**
   * Returns the flat colour for a kind of country.
   */
  private static Color tintOf(TerrainClass terrain) {
    return switch (terrain) {
      case OCEAN -> new Color(0x1B3A5C);
      case RIVER -> new Color(0x2E5C86);
      case BEACH -> new Color(0xD8C89A);
      case PLAINS -> new Color(0x7FA65A);
      case FOREST -> new Color(0x3F6B34);
      case TAIGA -> new Color(0x35604F);
      case JUNGLE -> new Color(0x2F7A3A);
      case SAVANNA -> new Color(0xA89B4E);
      case DESERT -> new Color(0xC9A66B);
      case SWAMP -> new Color(0x4C5F3A);
      case HILLS -> new Color(0x7C7460);
      case MOUNTAIN -> new Color(0x9A968F);
      case SNOW -> new Color(0xE2E8EC);
      case OTHER -> BARE;
    };
  }

  /**
   * Lights the surface from the north-west, using the drop to the sample up-slope of it.
   */
  private Color shade(Color base, int x, int z, int height) {
    int north = snapshot.heightOfSample(x, z - 1);
    int west = snapshot.heightOfSample(x - 1, z);
    int reference = 0;
    int counted = 0;

    if (north != WorldSnapshot.UNKNOWN_HEIGHT) {
      reference += north;
      counted++;
    }
    if (west != WorldSnapshot.UNKNOWN_HEIGHT) {
      reference += west;
      counted++;
    }

    if (counted == 0) {
      return base;
    }

    double difference = height - reference / (double) counted;
    double shade = Math.clamp(difference * SHADE_PER_BLOCK, -SHADE_LIMIT, SHADE_LIMIT);

    return shade >= 0
        ? lerp(base, Color.WHITE, shade)
        : lerp(base, Color.BLACK, -shade);
  }

  private static Color depthColour(int depth) {
    double unit = Math.clamp(depth / 24.0, 0.0, 1.0);
    return lerp(new Color(0x4B86B4), new Color(0x0E2438), unit);
  }

  private static Color blend(Color under, Color over, double weight) {
    return lerp(under, over, Math.clamp(weight, 0.0, 1.0));
  }

  private static Color lerp(Color from, Color to, double t) {
    return new Color(
        (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
        (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
        (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t)
    );
  }
}
