package com.edysmajler.neweracore.plan;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A raster of what the world generator puts on the ground, sampled once and saved to a file.
 *
 * <p>This exists because of a hard boundary. Everything NewEraCore invents — the corruption field,
 * impact zones, structure and town sites — is a pure function of the seed and can be recomputed
 * anywhere, including in a planning tool that has never seen a server. Height, biome and water are
 * <em>Mojang's</em> generator, reachable only through a loaded chunk or {@code getComputedBiome} on
 * a live world. No standalone tool can derive them from a seed without reimplementing vanilla
 * terrain, so the server exports them once and the planner reads the export.
 *
 * <p>Sampled on a grid rather than per block: at the default resolution one sample stands for a
 * four-by-four patch, which is fifteen times less data than every column and still finer than any
 * decision a designer makes when siting a town. {@link #resolution} is recorded in the file so a
 * reader never has to assume it.
 *
 * <p>The land and rugged bits are computed <em>by the server's own {@code LandLookup}</em> and
 * stored, rather than being re-derived from {@link #terrainAt} by the reader. Site selection tests
 * exactly those two questions, so storing the server's answers is what makes the planner's
 * predicted towns and structures land where the plugin will actually put them.
 */
public final class WorldSnapshot {

  /** File magic, "NEPS" — NewEra planner snapshot. */
  private static final int MAGIC = 0x4E455053;

  /** Format version, raised whenever the record layout changes. */
  private static final int VERSION = 1;

  /** Height stored for a sample that was never filled in. */
  public static final short UNKNOWN_HEIGHT = Short.MIN_VALUE;

  private static final int FLAG_LAND = 0x1;
  private static final int FLAG_RUGGED = 0x2;

  private final String worldName;
  private final long seed;
  private final int originX;
  private final int originZ;
  private final int size;
  private final int resolution;
  private final int samplesPerSide;
  private final short[] height;
  private final byte[] waterDepth;
  private final byte[] terrain;
  private final byte[] flags;
  private final byte[] corruption;
  private final byte[] impact;
  private final List<PlannedSite> sites;

  private WorldSnapshot(
      String worldName,
      long seed,
      int originX,
      int originZ,
      int size,
      int resolution,
      short[] height,
      byte[] waterDepth,
      byte[] terrain,
      byte[] flags,
      byte[] corruption,
      byte[] impact,
      List<PlannedSite> sites
  ) {
    this.worldName = worldName;
    this.seed = seed;
    this.originX = originX;
    this.originZ = originZ;
    this.size = size;
    this.resolution = resolution;
    this.samplesPerSide = size / resolution;
    this.height = height;
    this.waterDepth = waterDepth;
    this.terrain = terrain;
    this.flags = flags;
    this.corruption = corruption;
    this.impact = impact;
    this.sites = List.copyOf(sites);
  }

  /**
   * Returns the name of the world this was sampled from.
   *
   * @return the world name
   */
  public String worldName() {
    return worldName;
  }

  /**
   * Returns the seed the world was generated with.
   *
   * @return the world seed
   */
  public long seed() {
    return seed;
  }

  /**
   * Returns the block x of the snapshot's lowest corner.
   *
   * @return the origin x
   */
  public int originX() {
    return originX;
  }

  /**
   * Returns the block z of the snapshot's lowest corner.
   *
   * @return the origin z
   */
  public int originZ() {
    return originZ;
  }

  /**
   * Returns the width of the covered area in blocks.
   *
   * @return the size in blocks
   */
  public int size() {
    return size;
  }

  /**
   * Returns how many blocks one sample stands for.
   *
   * @return the resolution in blocks
   */
  public int resolution() {
    return resolution;
  }

  /**
   * Returns the number of samples along one edge.
   *
   * @return the grid edge
   */
  public int samplesPerSide() {
    return samplesPerSide;
  }

  /**
   * Returns whether a block position falls inside the snapshot.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true when the position is covered
   */
  public boolean contains(int blockX, int blockZ) {
    return blockX >= originX
        && blockZ >= originZ
        && blockX < originX + size
        && blockZ < originZ + size;
  }

  /**
   * Returns the ground height at a position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the surface y, or {@link #UNKNOWN_HEIGHT} outside the snapshot
   */
  public int heightAt(int blockX, int blockZ) {
    int index = indexOf(blockX, blockZ);
    return index < 0 ? UNKNOWN_HEIGHT : height[index];
  }

  /**
   * Returns how deep the water is at a position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the depth in blocks, 0 on dry land
   */
  public int waterDepthAt(int blockX, int blockZ) {
    int index = indexOf(blockX, blockZ);
    return index < 0 ? 0 : waterDepth[index] & 0xFF;
  }

  /**
   * Returns the kind of country at a position.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the terrain class, {@link TerrainClass#OTHER} outside the snapshot
   */
  public TerrainClass terrainAt(int blockX, int blockZ) {
    int index = indexOf(blockX, blockZ);
    return index < 0 ? TerrainClass.OTHER : TerrainClass.byOrdinal(terrain[index]);
  }

  /**
   * Returns whether the generator puts land rather than open water at a position.
   *
   * <p>Outside the snapshot this answers land, matching {@code LandLookup.of}: refusing to place
   * anything is a worse failure than placing it wrongly, and the planner's job is to show what the
   * plugin would do.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true on land
   */
  public boolean isLand(int blockX, int blockZ) {
    int index = indexOf(blockX, blockZ);
    return index < 0 || (flags[index] & FLAG_LAND) != 0;
  }

  /**
   * Returns whether the ground is broken enough that nothing wants to be built across it.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return true on hills, slopes and peaks
   */
  public boolean isRugged(int blockX, int blockZ) {
    int index = indexOf(blockX, blockZ);
    return index >= 0 && (flags[index] & FLAG_RUGGED) != 0;
  }

  /**
   * Returns the sample height for a grid cell, for a renderer walking the grid directly.
   *
   * @param sampleX grid x, 0 to {@link #samplesPerSide} exclusive
   * @param sampleZ grid z, 0 to {@link #samplesPerSide} exclusive
   * @return the surface y, or {@link #UNKNOWN_HEIGHT} off the grid
   */
  public int heightOfSample(int sampleX, int sampleZ) {
    return onGrid(sampleX, sampleZ) ? height[sampleZ * samplesPerSide + sampleX] : UNKNOWN_HEIGHT;
  }

  /**
   * Returns the sample water depth for a grid cell.
   *
   * @param sampleX grid x
   * @param sampleZ grid z
   * @return the depth in blocks, 0 on dry land or off the grid
   */
  public int waterOfSample(int sampleX, int sampleZ) {
    return onGrid(sampleX, sampleZ)
        ? waterDepth[sampleZ * samplesPerSide + sampleX] & 0xFF
        : 0;
  }

  /**
   * Returns the sample terrain class for a grid cell.
   *
   * @param sampleX grid x
   * @param sampleZ grid z
   * @return the terrain class
   */
  public TerrainClass terrainOfSample(int sampleX, int sampleZ) {
    return onGrid(sampleX, sampleZ)
        ? TerrainClass.byOrdinal(terrain[sampleZ * samplesPerSide + sampleX])
        : TerrainClass.OTHER;
  }

  /**
   * Returns the corruption field's percentile at a position.
   *
   * <p>Exported rather than recomputed so the planner needs neither the noise configuration nor a
   * calibration pass of its own: the field is a pure function of the seed, but its thresholds only
   * mean what they look like after {@code NoiseField} has calibrated against thousands of samples,
   * and two independently calibrated copies are two chances to disagree.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the value between 0 and 1, 0 outside the snapshot
   */
  public double corruptionAt(int blockX, int blockZ) {
    int index = indexOf(blockX, blockZ);
    return index < 0 ? 0.0 : (corruption[index] & 0xFF) / 255.0;
  }

  /**
   * Returns the impact field's percentile at a position, which is where craters cluster.
   *
   * @param blockX absolute block x
   * @param blockZ absolute block z
   * @return the value between 0 and 1, 0 outside the snapshot
   */
  public double impactAt(int blockX, int blockZ) {
    int index = indexOf(blockX, blockZ);
    return index < 0 ? 0.0 : (impact[index] & 0xFF) / 255.0;
  }

  /**
   * Returns the corruption percentile for a grid cell.
   *
   * @param sampleX grid x
   * @param sampleZ grid z
   * @return the value between 0 and 1
   */
  public double corruptionOfSample(int sampleX, int sampleZ) {
    return onGrid(sampleX, sampleZ)
        ? (corruption[sampleZ * samplesPerSide + sampleX] & 0xFF) / 255.0
        : 0.0;
  }

  /**
   * Returns the impact percentile for a grid cell.
   *
   * @param sampleX grid x
   * @param sampleZ grid z
   * @return the value between 0 and 1
   */
  public double impactOfSample(int sampleX, int sampleZ) {
    return onGrid(sampleX, sampleZ)
        ? (impact[sampleZ * samplesPerSide + sampleX] & 0xFF) / 255.0
        : 0.0;
  }

  /**
   * Returns every feature the plugin will place inside the snapshot.
   *
   * @return the sites, in the order the server resolved them
   */
  public List<PlannedSite> sites() {
    return sites;
  }

  private boolean onGrid(int sampleX, int sampleZ) {
    return sampleX >= 0 && sampleZ >= 0 && sampleX < samplesPerSide && sampleZ < samplesPerSide;
  }

  private int indexOf(int blockX, int blockZ) {
    if (!contains(blockX, blockZ)) {
      return -1;
    }

    int sampleX = (blockX - originX) / resolution;
    int sampleZ = (blockZ - originZ) / resolution;
    return sampleZ * samplesPerSide + sampleX;
  }

  /**
   * Writes the snapshot to a gzipped file.
   *
   * @param file where to write
   * @throws IOException when the file cannot be written
   */
  public void write(Path file) throws IOException {
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    try (OutputStream raw = Files.newOutputStream(file);
        OutputStream zipped = new GZIPOutputStream(new BufferedOutputStream(raw));
        DataOutputStream out = new DataOutputStream(zipped)) {
      out.writeInt(MAGIC);
      out.writeInt(VERSION);
      out.writeUTF(worldName);
      out.writeLong(seed);
      out.writeInt(originX);
      out.writeInt(originZ);
      out.writeInt(size);
      out.writeInt(resolution);

      for (int i = 0; i < height.length; i++) {
        out.writeShort(height[i]);
        out.writeByte(waterDepth[i]);
        out.writeByte(terrain[i]);
        out.writeByte(flags[i]);
        out.writeByte(corruption[i]);
        out.writeByte(impact[i]);
      }

      out.writeInt(sites.size());
      for (PlannedSite site : sites) {
        out.writeByte(site.kind().ordinal());
        out.writeUTF(site.id());
        out.writeInt(site.centerX());
        out.writeInt(site.centerZ());
        out.writeInt(site.radius());
        out.writeByte(site.rotation());
      }
    }
  }

  /**
   * Reads a snapshot written by {@link #write}.
   *
   * @param file the file to read
   * @return the snapshot
   * @throws IOException when the file cannot be read or is not a snapshot
   */
  public static WorldSnapshot read(Path file) throws IOException {
    try (InputStream raw = Files.newInputStream(file);
        InputStream zipped = new GZIPInputStream(new BufferedInputStream(raw));
        DataInputStream in = new DataInputStream(zipped)) {
      if (in.readInt() != MAGIC) {
        throw new IOException(file + " is not a NewEra world snapshot");
      }

      int version = in.readInt();
      if (version != VERSION) {
        throw new IOException("Snapshot version " + version + " cannot be read by this build, "
            + "which writes version " + VERSION + ": export it again");
      }

      final String worldName = in.readUTF();
      final long seed = in.readLong();
      final int originX = in.readInt();
      final int originZ = in.readInt();
      int size = in.readInt();
      int resolution = in.readInt();

      if (resolution <= 0 || size <= 0 || size % resolution != 0) {
        throw new IOException("Snapshot header is inconsistent: size " + size
            + " at resolution " + resolution);
      }

      int samples = (size / resolution) * (size / resolution);
      short[] height = new short[samples];
      byte[] waterDepth = new byte[samples];
      byte[] terrain = new byte[samples];
      byte[] flags = new byte[samples];
      byte[] corruption = new byte[samples];
      byte[] impact = new byte[samples];

      for (int i = 0; i < samples; i++) {
        height[i] = in.readShort();
        waterDepth[i] = in.readByte();
        terrain[i] = in.readByte();
        flags[i] = in.readByte();
        corruption[i] = in.readByte();
        impact[i] = in.readByte();
      }

      int siteCount = in.readInt();
      List<PlannedSite> sites = new ArrayList<>(Math.max(0, Math.min(siteCount, 1 << 16)));
      for (int i = 0; i < siteCount; i++) {
        sites.add(new PlannedSite(
            PlannedSite.SiteKind.byOrdinal(in.readByte()),
            in.readUTF(),
            in.readInt(),
            in.readInt(),
            in.readInt(),
            in.readByte()
        ));
      }

      return new WorldSnapshot(worldName, seed, originX, originZ, size, resolution,
          height, waterDepth, terrain, flags, corruption, impact, sites);
    }
  }

  /**
   * Collects samples into a snapshot.
   *
   * <p>Separate from the snapshot itself so a half-sampled export is never mistakable for a
   * finished one: the export command fills a builder over many ticks and only calls {@link #build}
   * when the scan completes.
   */
  public static final class Builder {

    private final String worldName;
    private final long seed;
    private final int originX;
    private final int originZ;
    private final int size;
    private final int resolution;
    private final int samplesPerSide;
    private final short[] height;
    private final byte[] waterDepth;
    private final byte[] terrain;
    private final byte[] flags;
    private final byte[] corruption;
    private final byte[] impact;
    private final List<PlannedSite> sites = new ArrayList<>();

    /**
     * Creates a builder over an area.
     *
     * @param worldName the world being sampled
     * @param seed the world seed
     * @param originX block x of the area's lowest corner
     * @param originZ block z of the area's lowest corner
     * @param size width of the area in blocks; rounded up to a whole number of samples
     * @param resolution how many blocks one sample stands for
     */
    public Builder(String worldName, long seed, int originX, int originZ, int size,
        int resolution) {
      int step = Math.max(1, resolution);
      final int edge = Math.max(1, (size + step - 1) / step);

      this.worldName = worldName;
      this.seed = seed;
      this.originX = originX;
      this.originZ = originZ;
      this.resolution = step;
      this.samplesPerSide = edge;
      this.size = edge * step;
      int count = edge * edge;
      this.height = new short[count];
      this.waterDepth = new byte[count];
      this.terrain = new byte[count];
      this.flags = new byte[count];
      this.corruption = new byte[count];
      this.impact = new byte[count];

      Arrays.fill(height, UNKNOWN_HEIGHT);
      Arrays.fill(terrain, (byte) TerrainClass.OTHER.ordinal());
    }

    /**
     * Returns the grid edge, so a caller can walk samples rather than blocks.
     *
     * @return the number of samples along one edge
     */
    public int samplesPerSide() {
      return samplesPerSide;
    }

    /**
     * Returns how many blocks one sample stands for.
     *
     * @return the resolution in blocks
     */
    public int resolution() {
      return resolution;
    }

    /**
     * Returns the block x a sample column covers.
     *
     * @param sampleX grid x
     * @return the absolute block x
     */
    public int blockXofSample(int sampleX) {
      return originX + sampleX * resolution;
    }

    /**
     * Returns the block z a sample row covers.
     *
     * @param sampleZ grid z
     * @return the absolute block z
     */
    public int blockZofSample(int sampleZ) {
      return originZ + sampleZ * resolution;
    }

    /**
     * Records one sample.
     *
     * @param sampleX grid x
     * @param sampleZ grid z
     * @param surfaceY the ground height, water surface included
     * @param water how deep the water is, 0 on dry land
     * @param terrainClass the kind of country
     * @param land whether the generator puts land here
     * @param rugged whether the ground is broken
     * @param corruptionValue the corruption field's percentile, 0 to 1
     * @param impactValue the impact field's percentile, 0 to 1
     */
    public void set(int sampleX, int sampleZ, int surfaceY, int water, TerrainClass terrainClass,
        boolean land, boolean rugged, double corruptionValue, double impactValue) {
      if (sampleX < 0 || sampleZ < 0 || sampleX >= samplesPerSide || sampleZ >= samplesPerSide) {
        return;
      }

      int index = sampleZ * samplesPerSide + sampleX;
      height[index] = (short) Math.clamp(surfaceY, Short.MIN_VALUE + 1, Short.MAX_VALUE);
      waterDepth[index] = (byte) Math.clamp(water, 0, 255);
      terrain[index] = (byte) terrainClass.ordinal();
      flags[index] = (byte) ((land ? FLAG_LAND : 0) | (rugged ? FLAG_RUGGED : 0));
      corruption[index] = quantise(corruptionValue);
      impact[index] = quantise(impactValue);
    }

    /**
     * Records a feature the plugin will place.
     *
     * @param site the resolved site
     */
    public void add(PlannedSite site) {
      sites.add(site);
    }

    private static byte quantise(double unit) {
      return (byte) Math.clamp(Math.round(unit * 255.0), 0, 255);
    }

    /**
     * Freezes the collected samples into a snapshot.
     *
     * @return the snapshot
     */
    // The builder is spent once built and never mutated again, so the snapshot may keep its
    // arrays: copying half a million samples to restate that would double the export's peak
    // memory for nothing.
    public WorldSnapshot build() {
      return new WorldSnapshot(worldName, seed, originX, originZ, size, resolution,
          height, waterDepth, terrain, flags, corruption, impact, sites);
    }
  }
}
