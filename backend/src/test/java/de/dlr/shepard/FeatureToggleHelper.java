package de.dlr.shepard;

import org.eclipse.microprofile.config.ConfigProvider;

public class FeatureToggleHelper {

  public static boolean isExperimentalTimeseriesEnabled() {
    return isToggleEnabled("shepard.experimental-timeseries.enabled");
  }

  private static boolean isToggleEnabled(String toggleProperty) {
    String propertyEnabled = ConfigProvider.getConfig().getValue(toggleProperty, String.class);
    return "true".equals(propertyEnabled);
  }
}
