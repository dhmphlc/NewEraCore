package com.edysmajler.neweracore.world.feature;

/**
 * A chunk-relative block position.
 *
 * @param x chunk-relative x, 0-15
 * @param y absolute height
 * @param z chunk-relative z, 0-15
 */
public record BlockPosition(int x, int y, int z) {}
