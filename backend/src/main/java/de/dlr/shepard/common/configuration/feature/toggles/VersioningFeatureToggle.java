package de.dlr.shepard.common.configuration.feature.toggles;

public class VersioningFeatureToggle {

  public static final String TOGGLE_PROPERTY = "shepard.versioning.enabled";

  public static final String IS_ENABLED_METHOD_ID =
    "de.dlr.shepard.common.configuration.feature.toggles.VersioningFeatureToggle#isEnabled";

  public static boolean isEnabled() {
    return TogglePropertyUtil.isToggleEnabled(TOGGLE_PROPERTY);
  }
}
