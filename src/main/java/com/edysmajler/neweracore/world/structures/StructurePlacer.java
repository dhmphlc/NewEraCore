package com.edysmajler.neweracore.world.structures;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ChunkProcessor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.World;

/**
 * Builds each structure the moment the last chunk of its footprint generates.
 *
 * <p>Everything else in the pipeline works one chunk at a time and renders its own slice, which is
 * the right shape for continuous things — ash, roads, craters. A structure is the opposite kind of
 * thing: a discrete object whose whole point is its silhouette, and a silhouette assembled from
 * independently-rendered slices is one bug away from a wreck with a seam through it. So a structure
 * is placed <em>whole</em>, exactly once, and the per-chunk pass is only the trigger.
 *
 * <p>Each chunk a footprint touches asks the same question when it generates: is every chunk of the
 * footprint generated now? While many are missing, nothing happens. Once only a few stragglers
 * remain, the placer generates those itself and builds at once — waiting for a player to sweep
 * generation across the very last corner chunk shipped as "I teleported to the site and nothing is
 * there", because view distance pushes generation outward unevenly and the far corner of a
 * footprint can stay ungenerated for as long as the player stands still. The site is marked placed
 * <em>before</em> the stragglers are generated, so when their own pipeline runs this placer again
 * re-entrantly, the mark stops it from starting the same placement twice. The structure is still
 * built over fully transformed terrain: each straggler runs its whole pipeline as it generates,
 * before the building starts.
 *
 * <p>The placement mark settles the race two same-tick footprint chunks would otherwise have, and
 * it is written <em>before</em> building for the same reason the engine marks chunks before
 * transforming them: a failing build must not leave the site eligible for a second, compounding
 * attempt.
 */
public class StructurePlacer implements ChunkProcessor {

  /**
   * How many ungenerated footprint chunks the placer will generate itself to finish a site.
   *
   * <p>Above this the site simply waits for the player to come closer — generating half a
   * footprint in one tick for someone skirting its far edge is a lag spike for something they may
   * never look at.
   */
  private static final int COMPLETION_BUDGET = 8;

  private final StructureManager structures;
  private final StructureMarker marker;
  private final Logger logger;

  /**
   * Creates the placer.
   *
   * @param structures the registry of what can be placed
   * @param marker the placed-site marker
   * @param logger the logger that reports each placement
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The plugin's logger is shared by design; copying it is not possible."
  )
  public StructurePlacer(StructureManager structures, StructureMarker marker, Logger logger) {
    this.structures = structures;
    this.marker = marker;
    this.logger = logger;
  }

  @Override
  public String name() {
    return "structures";
  }

  @Override
  public void process(ChunkContext context) {
    World world = context.world();

    for (StructureSite site : context.structureSites()) {
      List<long[]> missing = missingFootprint(world, site);
      if (missing.size() > COMPLETION_BUDGET || marker.isPlaced(world, site)) {
        continue;
      }

      marker.markPlaced(world, site);

      // Finish the footprint ourselves. Each chunk generated here fires its own load event and
      // runs the full pipeline — including this placer, which the mark above turns away — so by
      // the time the building starts, every chunk under it is finished terrain.
      for (long[] chunk : missing) {
        world.getChunkAt((int) chunk[0], (int) chunk[1]);
      }

      structures.byId(site.structureId()).ifPresent(definition -> {
        definition.place(new StructureField(world, site), site);
        // One line per placement: rare enough to stay quiet, and the only way to verify a wreck
        // exists without flying out to it
        logger.info(() -> "Placed " + site.structureId()
            + " at " + site.centerX() + ", " + site.centerZ());
      });
    }
  }

  /**
   * Returns the footprint chunks that do not exist yet, giving up once the count passes the
   * completion budget.
   *
   * <p>Generated is the question, not loaded: a generated chunk can be written into at the cost of
   * a load, but writing into an ungenerated one mid-placement would run its pipeline over the
   * freshly built structure and bury it — which is why the stragglers are generated <em>before</em>
   * building, never during.
   */
  private static List<long[]> missingFootprint(World world, StructureSite site) {
    int minX = (site.centerX() - site.radius()) >> 4;
    int maxX = (site.centerX() + site.radius()) >> 4;
    int minZ = (site.centerZ() - site.radius()) >> 4;
    int maxZ = (site.centerZ() + site.radius()) >> 4;

    List<long[]> missing = new ArrayList<>();

    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        if (!world.isChunkGenerated(x, z)) {
          missing.add(new long[] {x, z});

          if (missing.size() > COMPLETION_BUDGET) {
            return missing;
          }
        }
      }
    }

    return missing;
  }
}
