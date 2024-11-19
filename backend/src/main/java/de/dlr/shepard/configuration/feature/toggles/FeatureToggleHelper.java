package de.dlr.shepard.configuration.feature.toggles;

import org.eclipse.microprofile.config.ConfigProvider;

class FeatureToggleHelper {

  static boolean isToggleEnabled(String toggleProperty) {
    String propertyEnabled = ConfigProvider.getConfig().getValue(toggleProperty, String.class);
    return "true".equals(propertyEnabled);
  }
}
