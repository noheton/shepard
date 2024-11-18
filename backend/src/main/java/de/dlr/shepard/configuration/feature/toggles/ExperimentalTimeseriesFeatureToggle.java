package de.dlr.shepard.configuration.feature.toggles;

public class ExperimentalTimeseriesFeatureToggle {

  public static final String TOGGLE_PROPERTY = "shepard.experimental-timeseries.enabled";

  public static final String IS_ENABLED_METHOD_ID =
    "de.dlr.shepard.configuration.feature.toggles.ExperimentalTimeseriesFeatureToggle#isEnabled";

  public static boolean isEnabled() {
    return FeatureToggleHelper.isToggleEnabled(TOGGLE_PROPERTY);
  }
}
