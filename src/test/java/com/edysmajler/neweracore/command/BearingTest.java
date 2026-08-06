package com.edysmajler.neweracore.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BearingTest {

  @Test
  void northIsNegativeZ() {
    // The detail every hand-rolled compass gets backwards, and one that would send a player looking
    // for a crater in exactly the wrong direction
    assertEquals("north", Bearing.of(0, -100));
    assertEquals("south", Bearing.of(0, 100));
  }

  @Test
  void eastIsPositiveX() {
    assertEquals("east", Bearing.of(100, 0));
    assertEquals("west", Bearing.of(-100, 0));
  }

  @Test
  void diagonalsReadAsDiagonals() {
    assertEquals("north-east", Bearing.of(100, -100));
    assertEquals("north-west", Bearing.of(-100, -100));
    assertEquals("south-east", Bearing.of(100, 100));
    assertEquals("south-west", Bearing.of(-100, 100));
  }

  @Test
  void noOffsetHasNoDirection() {
    assertEquals("here", Bearing.of(0, 0));
  }
}
