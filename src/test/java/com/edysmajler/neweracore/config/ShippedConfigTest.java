package com.edysmajler.neweracore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edysmajler.neweracore.plan.LocationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Parses the {@code config.yml} that actually ships, rather than the defaults in the Java classes.
 *
 * <p>The two are different things and only one of them reaches a server. Every other config test
 * here constructs {@link PluginConfig} directly, which cannot notice a mistyped key, a wrongly
 * indented block, or a documented value that its own validation annotations reject — all of which
 * turn into a plugin that refuses to enable, discovered by whoever restarts the server next.
 */
class ShippedConfigTest {

  private static PluginConfig config;

  @BeforeAll
  static void load() throws IOException {
    try (InputStream shipped = ShippedConfigTest.class.getResourceAsStream("/config.yml")) {
      assertNotNull(shipped, "config.yml is not on the classpath");
      config = new ObjectMapper(new YAMLFactory()).readValue(shipped, PluginConfig.class);
    }
  }

  @Test
  @DisplayName("the shipped config passes its own validation")
  void isValid() {
    // ParameterMessageInterpolator because the expression-language implementation is a runtime
    // dependency the server provides and the test classpath does not; the constraints themselves
    // are what is under test, not how their messages are worded.
    try (var factory = Validation.byDefaultProvider()
        .configure()
        .messageInterpolator(new ParameterMessageInterpolator())
        .buildValidatorFactory()) {
      Validator validator = factory.getValidator();
      Set<ConstraintViolation<PluginConfig>> violations = validator.validate(config);

      assertTrue(violations.isEmpty(), () -> "the shipped config violates its own constraints: "
          + violations.stream()
              .map(v -> v.getPropertyPath() + " " + v.getMessage())
              .collect(Collectors.joining(", ")));
    }
  }

  @Test
  @DisplayName("the plan block parses into the plan settings")
  void planBlockParses() {
    PlanConfig plan = config.getWorldEngine().getPlan();

    assertTrue(plan.isEnabled());
    assertEquals("", plan.getFile());
    assertEquals(32, plan.getClearance());
  }

  @Test
  @DisplayName("the documented builders reach the mapping, keyed by planned type")
  void buildersParse() {
    // An enum-keyed map is the part most likely to break quietly: a misspelled type name in YAML
    // would leave the map short and place nothing, with the config still looking correct
    assertEquals("fighter_jet",
        config.getWorldEngine().getPlan().builderFor(LocationType.CRASH_SITE));
  }

  @Test
  @DisplayName("the blocks the engine reads are all present")
  void allSectionsParse() {
    WorldEngineConfig engine = config.getWorldEngine();

    assertNotNull(engine.getNoise());
    assertNotNull(engine.getThresholds());
    assertNotNull(engine.getLevels());
    assertNotNull(engine.getStructures());
    assertNotNull(engine.getHugeCraters());
    assertNotNull(engine.getTowns());
    assertNotNull(engine.getOres());
    assertNotNull(engine.getPlan());
  }
}
