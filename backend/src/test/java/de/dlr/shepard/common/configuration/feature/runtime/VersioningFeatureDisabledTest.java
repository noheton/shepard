package de.dlr.shepard.common.configuration.feature.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest(FeatureBeanProducer.class)
@TestConfigProperty(key = "shepard.features.versioning.enabled", value = "false")
public class VersioningFeatureDisabledTest {

  @Inject
  @ConditionalOnFeature("versioning")
  VersioningFeature versioning;

  @Test
  public void producesDisabledFeatureBean() {
    assertNotNull(versioning);
    assertFalse(versioning.isEnabled());
  }
}
