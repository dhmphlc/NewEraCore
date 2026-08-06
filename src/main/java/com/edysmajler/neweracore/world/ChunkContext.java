package com.edysmajler.neweracore.world;

import com.edysmajler.neweracore.config.WorldEngineConfig;
import com.edysmajler.neweracore.world.corruption.CorruptionLevel;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import com.edysmajler.neweracore.world.corruption.CorruptionZone;
import com.edysmajler.neweracore.world.feature.CraterSite;
import com.edysmajler.neweracore.world.noise.NoiseFields;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

/**
 * Per-chunk working state handed to every {@link ChunkProcessor}.
 *
 * <p>Reads come from a {@link ChunkSnapshot} rather than live {@code Block} objects, which keeps
 * scanning to plain array access. Writes go straight to the world with physics disabled and are
 * mirrored into an overlay map so a later processor sees what an earlier one changed.
 *
 * <p>Placement decisions come from the noise masks, not from the random source: the masks decide
 * <em>where</em> ground dies, which stands of trees burn, and where craters land, so those things
 * appear as patches and regions. The random source is only used for texture inside an already
 * chosen
 * area — which of two dead materials to place, how long a fallen log is — and is seeded from the
 * world seed and chunk coordinates so a chunk always transforms identically.
 */
public class ChunkContext {

  /** Width and length of a chunk in blocks. */
  public static final int CHUNK_SIZE = 16;

  private final Chunk chunk;
  private final ChunkSnapshot snapshot;
  private final WorldEngineConfig config;
  private final CorruptionZone zone;
  private final Random random;
  private final int minHeight;
  private final int maxHeight;
  private final Map<Integer, Material> overlay = new HashMap<>();

  private final int[] groundHeights = new int[CHUNK_SIZE * CHUNK_SIZE];
  private boolean groundHeightsFound;

  private final int originX;
  private final int originZ;

  private final List<CraterSite> hugeCraterSites;

  private final ColumnMasks patchMask;
  private final ColumnMasks blightMask;
  private final ColumnMasks detailMask;
  private final double impact;

  /**
   * Creates a context for one chunk.
   *
   * @param chunk the chunk being transformed
   * @param config the world engine settings
   * @param fields the world's noise fields
   * @param zone the chunk's resolved corruption zone
   * @param hugeCraterSites the world-scale impact sites reaching this chunk
   */
  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP2", "PREDICTABLE_RANDOM"},
      justification = "The chunk is a live world handle that must be kept to write blocks, and the "
          + "seeded java.util.Random is deliberate: transformation must be reproducible per chunk, "
          + "and none of it is security sensitive."
  )
  public ChunkContext(
      Chunk chunk,
      WorldEngineConfig config,
      NoiseFields fields,
      CorruptionZone zone,
      List<CraterSite> hugeCraterSites
  ) {
    this.chunk = chunk;
    // Height map is required for surfaceY; biome data drives transformer selection
    this.snapshot = chunk.getChunkSnapshot(true, true, false);
    this.config = config;
    this.zone = zone;
    this.hugeCraterSites = List.copyOf(hugeCraterSites);
    this.minHeight = chunk.getWorld().getMinHeight();
    this.maxHeight = chunk.getWorld().getMaxHeight() - 1;

    long worldSeed = chunk.getWorld().getSeed();
    this.random = new Random(
        worldSeed ^ (chunk.getX() * 341873128712L + chunk.getZ() * 132897987541L)
    );

    this.originX = chunk.getX() * CHUNK_SIZE;
    this.originZ = chunk.getZ() * CHUNK_SIZE;
    this.patchMask = new ColumnMasks(fields.patch(), originX, originZ);
    this.blightMask = new ColumnMasks(fields.blight(), originX, originZ);
    this.detailMask = new ColumnMasks(fields.detail(), originX, originZ);
    this.impact = fields.impact().sample(originX + 8.0, originZ + 8.0);
  }

  /**
   * Returns the absolute world x of a chunk-relative column.
   *
   * @param x chunk-relative x, 0-15
   * @return the absolute block x
   */
  public int blockX(int x) {
    return originX + x;
  }

  /**
   * Returns the absolute world z of a chunk-relative column.
   *
   * @param z chunk-relative z, 0-15
   * @return the absolute block z
   */
  public int blockZ(int z) {
    return originZ + z;
  }

  /**
   * Returns the huge impact sites that reach this chunk, usually none.
   *
   * @return the sites
   */
  public List<CraterSite> hugeCraterSites() {
    return hugeCraterSites;
  }

  public WorldEngineConfig getConfig() {
    return config;
  }

  /**
   * Returns the corruption level this chunk belongs to.
   *
   * @return the level
   */
  public CorruptionLevel level() {
    return zone.level();
  }

  /**
   * Returns the effective rules for this chunk, already blended for a smooth level transition.
   *
   * @return the profile
   */
  public CorruptionProfile profile() {
    return zone.profile();
  }

  /**
   * Returns how deep into its level band this chunk sits.
   *
   * @return the intensity between 0 and 1
   */
  public double intensity() {
    return zone.intensity();
  }

  /**
   * Returns the ground patch value of a column.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return the value between 0 and 1
   */
  public double patchAt(int x, int z) {
    return patchMask.at(x, z);
  }

  /**
   * Returns the tree blight value of a column, which groups tree damage into stands.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return the value between 0 and 1
   */
  public double blightAt(int x, int z) {
    return blightMask.at(x, z);
  }

  /**
   * Returns the fine detail value of a column, used to pick between materials inside a patch.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return the value between 0 and 1
   */
  public double detailAt(int x, int z) {
    return detailMask.at(x, z);
  }

  /**
   * Returns this chunk's impact value, sampled once at its centre.
   *
   * @return the value between 0 and 1
   */
  public double impact() {
    return impact;
  }

  /**
   * Returns the next uniform value in [0, 1).
   *
   * @return the rolled value
   */
  public double nextDouble() {
    return random.nextDouble();
  }

  /**
   * Returns a random boolean, used for coin-flip choices such as a log's axis.
   *
   * @return the rolled value
   */
  public boolean nextBoolean() {
    return random.nextBoolean();
  }

  public int getMinHeight() {
    return minHeight;
  }

  public int getMaxHeight() {
    return maxHeight;
  }

  /**
   * Rolls against a probability.
   *
   * @param chance probability between 0 and 1
   * @return true if the roll succeeded
   */
  public boolean chance(double chance) {
    if (chance <= 0.0) {
      return false;
    }
    return chance >= 1.0 || random.nextDouble() < chance;
  }

  /**
   * Returns a random value in an inclusive range.
   *
   * @param min lower bound
   * @param max upper bound, clamped up to {@code min} when smaller
   * @return the rolled value
   */
  public int between(int min, int max) {
    int high = Math.max(min, max);
    return min + random.nextInt(high - min + 1);
  }

  /**
   * Turns an expected count into a whole number, keeping the fraction as a chance.
   *
   * <p>An expected 1.2 logs per chunk becomes one log always and a second one fifth of the time,
   * which lets a level be tuned below one occurrence per chunk.
   *
   * @param expected the expected count
   * @return the rolled count
   */
  public int count(double expected) {
    int whole = (int) Math.floor(expected);
    return chance(expected - whole) ? whole + 1 : whole;
  }

  /**
   * Returns the highest non-air block height in a column.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return the surface height
   */
  public int surfaceY(int x, int z) {
    return Math.min(maxHeight, snapshot.getHighestBlockYAt(x, z));
  }

  /**
   * Returns the height of the actual ground in a column, ignoring anything growing on it.
   *
   * <p>{@link #surfaceY} returns the highest block, which under a tree is the top of its canopy.
   * Reading the ground from there is a mistake that hid an entire class of bug: passes that inspect
   * the surface found leaves, decided there was nothing they could treat, and left every column
   * beneath a forest as untouched vanilla ground. A dense birch wood came out completely unchanged.
   *
   * <p>This walks down past canopy, trunks, and undergrowth to the first block that is really the
   * floor. Water stops the walk, so a lake surface is the "ground" for anything that must not act
   * underwater.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return the height of the ground
   */
  public int groundY(int x, int z) {
    if (!groundHeightsFound) {
      findGroundHeights();
    }

    return groundHeights[(x << 4) | z];
  }

  /**
   * Returns the lowest height the engine inspects in a column.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return the floor of the scanned band
   */
  public int scanFloor(int x, int z) {
    return Math.max(minHeight, surfaceY(x, z) - config.getScanDepth());
  }

  /**
   * Returns the biome of a column.
   *
   * @param x chunk-relative x, 0-15
   * @param z chunk-relative z, 0-15
   * @return the biome at that column's surface
   */
  public Biome biomeAt(int x, int z) {
    return snapshot.getBiome(x, surfaceY(x, z), z);
  }

  /**
   * Returns the current material at a position, including edits made during this pass.
   *
   * @param x chunk-relative x, 0-15
   * @param y absolute height
   * @param z chunk-relative z, 0-15
   * @return the material, or air when outside the world's height range
   */
  public Material typeAt(int x, int y, int z) {
    if (y < minHeight || y > maxHeight || !inChunk(x, z)) {
      return Material.AIR;
    }

    Material edited = overlay.get(index(x, y, z));
    return edited != null ? edited : snapshot.getBlockType(x, y, z);
  }

  /**
   * Replaces a block without triggering physics.
   *
   * <p>No-ops when the target already holds that material, so repeated passes do not queue
   * redundant block changes.
   *
   * @param x chunk-relative x, 0-15
   * @param y absolute height
   * @param z chunk-relative z, 0-15
   * @param material the material to place
   */
  public void set(int x, int y, int z, Material material) {
    if (y < minHeight || y > maxHeight || !inChunk(x, z)) {
      return;
    }

    if (typeAt(x, y, z) == material) {
      return;
    }

    chunk.getBlock(x, y, z).setType(material, false);
    overlay.put(index(x, y, z), material);
  }

  /**
   * Replaces a block with specific block data, without triggering physics.
   *
   * <p>Used where orientation matters, such as logs lying on their side.
   *
   * @param x chunk-relative x, 0-15
   * @param y absolute height
   * @param z chunk-relative z, 0-15
   * @param data the block data to place
   */
  public void set(int x, int y, int z, BlockData data) {
    if (y < minHeight || y > maxHeight || !inChunk(x, z)) {
      return;
    }

    chunk.getBlock(x, y, z).setBlockData(data, false);
    overlay.put(index(x, y, z), data.getMaterial());
  }

  /**
   * Returns whether chunk-relative horizontal coordinates fall inside this chunk.
   *
   * @param x chunk-relative x
   * @param z chunk-relative z
   * @return true when both coordinates are within 0-15
   */
  public boolean inChunk(int x, int z) {
    return x >= 0 && x < CHUNK_SIZE && z >= 0 && z < CHUNK_SIZE;
  }

  private void findGroundHeights() {
    // Set first: the walk below reads block types, not ground state, so there is no recursion
    groundHeightsFound = true;

    for (int x = 0; x < CHUNK_SIZE; x++) {
      for (int z = 0; z < CHUNK_SIZE; z++) {
        groundHeights[(x << 4) | z] = walkDownToGround(x, z);
      }
    }
  }

  private int walkDownToGround(int x, int z) {
    int floor = Math.max(minHeight, surfaceY(x, z) - config.getScanDepth());

    for (int y = surfaceY(x, z); y > floor; y--) {
      if (!isCanopyOrClutter(snapshot.getBlockType(x, y, z))) {
        return y;
      }
    }

    return floor;
  }

  /**
   * Returns whether a material is something growing on the ground rather than the ground itself.
   */
  private static boolean isCanopyOrClutter(Material material) {
    if (material.isAir() || !material.isSolid()) {
      // Plants, carpets, and snow layers are not solid, so this covers undergrowth too
      return true;
    }

    return Tag.LOGS.isTagged(material)
        || Tag.LEAVES.isTagged(material)
        || Tag.WOOL_CARPETS.isTagged(material);
  }

  private int index(int x, int y, int z) {
    return ((y - minHeight) << 8) | (x << 4) | z;
  }
}
