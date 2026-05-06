package de.dlr.shepard.common.configuration.feature.toggles;

public class SpatialDataFeatureToggle {

  private static final String SPATIAL_DATA_PROPERTY = "shepard.infrastructure.spatial.enabled";

  public static boolean isActive() {
    return TogglePropertyUtil.isToggleEnabled(SPATIAL_DATA_PROPERTY);
  }
}
