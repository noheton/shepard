package de.dlr.shepard.common.configuration.feature.toggles;

import java.util.Optional;
import org.eclipse.microprofile.config.ConfigProvider;

class TogglePropertyUtil {

  static boolean isToggleEnabled(String toggleProperty) {
    Optional<String> propertyEnabled = ConfigProvider.getConfig().getOptionalValue(toggleProperty, String.class);
    return propertyEnabled.map(p -> "true".equals(p)).orElse(false);
  }
}
