package com.edysmajler.neweracore.world.feature;

/**
 * A trunk base: the lowest log of a tree standing on ground.
 *
 * <p>Whether this tree lives is not stored here. It is read from the blight field at its column, so
 * a
 * whole stand shares one answer and living groves come out as coherent islands.
 *
 * @param x chunk-relative x of the trunk, 0-15
 * @param y absolute height of the trunk's lowest log
 * @param z chunk-relative z of the trunk, 0-15
 */
public record Tree(int x, int y, int z) {}
