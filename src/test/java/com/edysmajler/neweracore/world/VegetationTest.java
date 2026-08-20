package com.edysmajler.neweracore.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class VegetationTest {

  @Test
  void engineLitterNeedsFooting() {
    // Both are placed by the engine itself, so no tag or fragility test will ever cover them —
    // and anything clear() leaves unswept hangs in the air, because physics is off for every
    // write. The pale moss carpet is the ash carpet: missing it shipped structures ringed by
    // carpets floating over their own crash scars.
    assertTrue(Vegetation.needsFooting(Material.PALE_MOSS_CARPET));
    assertTrue(Vegetation.needsFooting(Material.DEAD_BUSH));
  }

  @Test
  void hangingPlantsDoNotNeedFooting() {
    // A vine holds on to what is beside or above it; sweeping it with the block underneath would
    // punch holes in curtains that were anchored perfectly well. Only the vine is asserted: any
    // material that misses every named set falls through to org.bukkit.Tag, which cannot load
    // outside a running server.
    assertFalse(Vegetation.needsFooting(Material.VINE));
  }
}
