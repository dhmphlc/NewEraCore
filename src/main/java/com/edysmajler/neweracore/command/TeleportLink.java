package com.edysmajler.neweracore.command;

import java.util.Locale;

/**
 * Renders coordinates as a clickable teleport, the way vanilla {@code /locate} does.
 *
 * <p>A coordinate readout the player has to retype is a chore; clicking it should put the
 * teleport in the chat box ready to send. Suggest rather than run: the click fills the command in
 * for the player to confirm, so a stray click in chat does not fling anybody across the map.
 */
final class TeleportLink {

  private TeleportLink() {}

  /**
   * Returns the coordinates as MiniMessage text that suggests a teleport when clicked.
   *
   * <p>The height is left as {@code ~}: the site's ground level is not known without loading the
   * chunk, and the player's own height is as good a guess as any.
   *
   * @param x absolute block x
   * @param z absolute block z
   * @return the MiniMessage snippet
   */
  static String to(int x, int z) {
    return String.format(
        Locale.ROOT,
        "<click:suggest_command:'/tp @s %1$d ~ %2$d'><hover:show_text:'Click to teleport'>"
            + "<white>%1$d, %2$d</white></hover></click>",
        x,
        z
    );
  }
}
