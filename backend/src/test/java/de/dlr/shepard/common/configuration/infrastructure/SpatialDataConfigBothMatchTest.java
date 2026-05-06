package de.dlr.shepard.common.configuration.infrastructure;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest(SpatialDataConfig.class)
@TestConfigProperty(key = "shepard.infrastructure.spatial.enabled", value = "true")
@TestConfigProperty(key = "shepard.spatial-data.enabled", value = "true")
public class SpatialDataConfigBothMatchTest {

  static {
    SpatialDataConfigLogCapture.attach();
    SpatialDataConfigLogCapture.reset();
  }

  @Inject
  SpatialDataConfig config;

  @Test
  public void bothKeysWithSameValueResolveSilently() {
    assertNotNull(config);
    assertTrue(config.isEnabled());
    assertTrue(
      SpatialDataConfigLogCapture
        .warnings()
        .stream()
        .noneMatch(m -> m.contains("different values")),
      "expected no conflict warning, got: " + SpatialDataConfigLogCapture.warnings()
    );
  }
}
