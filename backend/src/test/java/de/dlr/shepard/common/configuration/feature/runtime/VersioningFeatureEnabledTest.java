package de.dlr.shepard.common.configuration.feature.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest(FeatureBeanProducer.class)
@TestConfigProperty(key = "shepard.features.versioning.enabled", value = "true")
public class VersioningFeatureEnabledTest {

  @Inject
  @ConditionalOnFeature("versioning")
  VersioningFeature versioning;

  @Test
  public void producesEnabledFeatureBean() {
    assertNotNull(versioning);
    assertTrue(versioning.isEnabled());
  }
}
