package com.edysmajler.neweracore.plan;

/**
 * A connection the designer wants built between two planned locations.
 *
 * <p>Stored as a pair of location ids rather than as a route. Which way a road actually runs is a
 * terrain question — where it can climb, where it needs a bridge — and answering it belongs to the
 * generator, which has the real ground. The plan says only that these two places should be
 * connected, which is the part a person is better at deciding than an algorithm: the previous
 * automatic network connected everything to everything and read as clutter.
 *
 * @param fromId id of the location the road starts at
 * @param toId id of the location it reaches
 */
public record PlannedRoad(String fromId, String toId) {}
