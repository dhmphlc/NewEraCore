package com.edysmajler.neweracore.command;

/**
 * Turns an offset into something a player can walk in.
 *
 * <p>"640 blocks away" is useless on its own; "640 blocks north-east" is a direction to set off in.
 * Note that north is <em>negative</em> z in Minecraft, which is the detail every hand-rolled
 * compass gets backwards.
 */
final class Bearing {

  private static final String[] POINTS = {
      "south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"
  };

  private Bearing() {}

  /**
   * Returns the compass direction of an offset.
   *
   * @param dx blocks east, negative for west
   * @param dz blocks south, negative for north
   * @return the direction name, or "here" when there is no offset to speak of
   */
  static String of(double dx, double dz) {
    if (Math.abs(dx) < 1.0 && Math.abs(dz) < 1.0) {
      return "here";
    }

    // atan2(x, z) measured from due south, stepped into eighths
    double angle = Math.toDegrees(Math.atan2(-dx, dz)) + 360.0;
    int step = (int) Math.round(angle / 45.0) % 8;

    return POINTS[step];
  }
}
