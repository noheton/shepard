package de.dlr.shepard.common.configuration.feature.toggles;

import java.util.Optional;
import org.eclipse.microprofile.config.ConfigProvider;

public class VersioningFeatureToggle {

  public static final String TOGGLE_PROPERTY = "shepard.features.versioning.enabled";

  public static final String IS_ENABLED_METHOD_ID =
    "de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle#isEnabled";

  public static boolean isEnabled() {
    Optional<Boolean> value = ConfigProvider.getConfig().getOptionalValue(TOGGLE_PROPERTY, Boolean.class);
    return value.orElse(true);
  }
}
