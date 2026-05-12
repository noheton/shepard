package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire-shape mirror of the backend's
 * {@code FeatureToggleIO} (lives in the backend module under
 * {@code de.dlr.shepard.v2.admin.io}). Decoupled so the CLI does
 * not pull in the backend Quarkus stack just for one DTO.
 */
public final class FeatureToggle {

  private final String name;
  private final boolean enabled;
  private final String description;

  public FeatureToggle(
    @JsonProperty("name") String name,
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("description") String description
  ) {
    this.name = name;
    this.enabled = enabled;
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getDescription() {
    return description;
  }
}
