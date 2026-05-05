package de.dlr.shepard.common.configuration.feature.runtime;

public class VersioningFeature {

  private final boolean enabled;

  public VersioningFeature() {
    this.enabled = false;
  }

  public VersioningFeature(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isEnabled() {
    return enabled;
  }
}
