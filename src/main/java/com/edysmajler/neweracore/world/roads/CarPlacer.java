package com.edysmajler.neweracore.world.roads;

import com.edysmajler.neweracore.world.ChunkContext;
import com.edysmajler.neweracore.world.ChunkProcessor;
import com.edysmajler.neweracore.world.structures.StructureField;
import com.edysmajler.neweracore.world.structures.loot.LootTable;
import org.bukkit.World;

/**
 * Builds each car the moment the last chunk of its small footprint generates.
 *
 * <p>The structure placer's discipline, sized down: mark first, build whole. One deliberate
 * difference — this placer never generates straggler chunks itself. Cars sit a few chunks apart
 * along every road, so a placer that completes footprints on demand would chain: finishing one
 * car's corner chunk starts the next car's, and one player stepping onto a highway generates the
 * corridor to the horizon. A car footprint is at most four chunks, the player is on the road it
 * decorates, and the generation disc sweeps those chunks naturally within moments — waiting is
 * free where chaining is not.
 */
public final class CarPlacer implements ChunkProcessor {

  private final PlacedMarker marker;
  private final LootTable loot;

  /**
   * Creates the placer.
   *
   * @param marker the placed-car marker
   * @param loot what a car's boot holds, or null to leave boots empty
   */
  public CarPlacer(PlacedMarker marker, LootTable loot) {
    this.marker = marker;
    this.loot = loot;
  }

  @Override
  public String name() {
    return "cars";
  }

  @Override
  public void process(ChunkContext context) {
    World world = context.world();

    for (CarSite car : context.roads().cars()) {
      if (!footprintGenerated(world, car) || marker.isPlaced(world, car.x(), car.z())) {
        continue;
      }

      marker.markPlaced(world, car.x(), car.z());
      CarWreck.place(new StructureField(world, car.seed()), car, loot);
    }
  }

  private static boolean footprintGenerated(World world, CarSite car) {
    int minX = (car.x() - CarSite.RADIUS) >> 4;
    int maxX = (car.x() + CarSite.RADIUS) >> 4;
    int minZ = (car.z() - CarSite.RADIUS) >> 4;
    int maxZ = (car.z() + CarSite.RADIUS) >> 4;

    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        if (!world.isChunkGenerated(x, z)) {
          return false;
        }
      }
    }

    return true;
  }
}
