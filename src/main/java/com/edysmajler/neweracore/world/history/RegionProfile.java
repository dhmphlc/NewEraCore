package com.edysmajler.neweracore.world.history;

import com.edysmajler.neweracore.world.corruption.CorruptionLevel;
import com.edysmajler.neweracore.world.corruption.CorruptionProfile;
import java.util.Optional;

/**
 * Everything the engine knows about what happened in one place.
 *
 * <p>This is the single answer to "what happened here?", and the whole point of it is that everyone
 * asks the same one. A pass that samples noise for itself is inventing its own private history, and
 * two systems doing that in the same valley will contradict each other — heavy ash from one,
 * thriving forest from another, a ruin from a third that fits neither. One profile, resolved once
 * per chunk and handed to every pass, is what makes a place feel like a place instead of a pile of
 * independent effects.
 *
 * <p>Immutable, and a pure function of the world seed and the coordinates. It can therefore be
 * resolved for anywhere at any time, including ground no player has ever loaded, which is what lets
 * a road know where it is going and a settlement know what it is sheltering from.
 *
 * <p>Note what is <em>not</em> here: the biome. Biomes are resolved per column, deliberately, so
 * that a chunk straddling a border is treated correctly on each side; folding a single biome group
 * into a region-wide fact would throw that away. The column dispatch already handles it.
 *
 * @param corruptionLevel how badly the region was hit, from the original corruption field
 * @param corruptionIntensity how deep into that level's band the region sits, 0 to 1
 * @param warIntensity how hard the fighting was here, 0 to 1
 * @param ashfall how much ash settled here, 0 to 1
 * @param restoration how much life held on here, 0 to 1
 * @param story the region's archetype, for systems that must choose between distinct things
 * @param profile the corruption numbers every pass runs on, already shaped by the history above
 * @param landmark the landmark this place stands on, if any
 * @param nearestLandmark the closest landmark, whether or not this place stands on it
 */
public record RegionProfile(
    CorruptionLevel corruptionLevel,
    double corruptionIntensity,
    double warIntensity,
    double ashfall,
    double restoration,
    RegionStory story,
    CorruptionProfile profile,
    Optional<Landmark> landmark,
    Optional<Landmark> nearestLandmark
) {

  /**
   * Returns whether this place stands on a landmark site.
   *
   * @return true when a landmark's footprint covers it
   */
  public boolean onLandmark() {
    return landmark.isPresent();
  }

  /**
   * Returns a one-line summary, for debug output.
   *
   * @return the description
   */
  public String describe() {
    return String.format(
        "%s / %s (war %.2f, ash %.2f, green %.2f)%s",
        story,
        corruptionLevel,
        warIntensity,
        ashfall,
        restoration,
        landmark.map(mark -> " at " + mark.type()).orElse("")
    );
  }
}
