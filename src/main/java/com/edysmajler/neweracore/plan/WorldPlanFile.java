package com.edysmajler.neweracore.plan;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes {@link WorldPlan} files as JSON.
 *
 * <p>JSON rather than the snapshot's packed binary, and pretty-printed, because a plan is meant to
 * be readable: a dozen decisions a person made, which that person may well want to diff, review, or
 * fix in a text editor. The snapshot is machine data and is packed; the plan is authorship and is
 * not.
 *
 * <p>Unknown properties are ignored on read, so a plan written by a newer planner still opens in an
 * older one with the parts it understands — the same forgiveness the schematic and loot loaders
 * apply, for the same reason.
 */
public final class WorldPlanFile {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(SerializationFeature.INDENT_OUTPUT);

  private WorldPlanFile() {}

  /**
   * Writes a plan.
   *
   * @param plan the plan to write
   * @param file where to write it
   * @throws IOException when the file cannot be written
   */
  public static void write(WorldPlan plan, Path file) throws IOException {
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    MAPPER.writeValue(file.toFile(), plan);
  }

  /**
   * Reads a plan.
   *
   * @param file the file to read
   * @return the plan
   * @throws IOException when the file cannot be read or is not a plan
   */
  public static WorldPlan read(Path file) throws IOException {
    return MAPPER.readValue(file.toFile(), WorldPlan.class);
  }
}
