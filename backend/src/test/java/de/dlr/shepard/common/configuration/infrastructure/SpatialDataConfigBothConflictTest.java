package de.dlr.shepard.common.configuration.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest(SpatialDataConfig.class)
@TestConfigProperty(key = "shepard.infrastructure.spatial.enabled", value = "false")
@TestConfigProperty(key = "shepard.spatial-data.enabled", value = "true")
public class SpatialDataConfigBothConflictTest {

  static {
    SpatialDataConfigLogCapture.attach();
    SpatialDataConfigLogCapture.reset();
  }

  @Inject
  SpatialDataConfig config;

  @Test
  public void newKeyWinsAndConflictWarningIsLogged() {
    assertNotNull(config);
    assertFalse(config.isEnabled(), "new key (false) should win over legacy (true)");
    assertTrue(
      SpatialDataConfigLogCapture
        .warnings()
        .stream()
        .anyMatch(m -> m.contains("different values")),
      "expected conflict warning, got: " + SpatialDataConfigLogCapture.warnings()
    );
  }
}
