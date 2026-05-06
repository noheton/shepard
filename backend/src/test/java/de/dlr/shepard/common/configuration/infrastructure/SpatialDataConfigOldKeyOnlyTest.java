package de.dlr.shepard.common.configuration.infrastructure;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest(SpatialDataConfig.class)
@TestConfigProperty(key = "shepard.spatial-data.enabled", value = "true")
public class SpatialDataConfigOldKeyOnlyTest {

  static {
    SpatialDataConfigLogCapture.attach();
    SpatialDataConfigLogCapture.reset();
  }

  @Inject
  SpatialDataConfig config;

  @Test
  public void oldKeyEnablesAndLogsDeprecationWarning() {
    assertNotNull(config);
    assertTrue(config.isEnabled());
    assertTrue(
      SpatialDataConfigLogCapture
        .warnings()
        .stream()
        .anyMatch(m -> m.contains("shepard.spatial-data.enabled") && m.contains("deprecated")),
      "expected deprecation warning, got: " + SpatialDataConfigLogCapture.warnings()
    );
  }
}
