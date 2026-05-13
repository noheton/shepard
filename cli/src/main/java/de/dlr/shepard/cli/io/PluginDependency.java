package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PM1c — CLI-side mirror of
 * {@code de.dlr.shepard.v2.admin.plugins.io.PluginDependencyIO}.
 *
 * <p>Same decoupling pattern as {@link PluginInfo} — the CLI doesn't
 * pull in Quarkus / JAX-RS for one DTO. Unknown fields ignored so a
 * forward-rev backend stays readable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PluginDependency {

  private final String pluginId;
  private final String versionConstraint;

  public PluginDependency(
    @JsonProperty("pluginId") String pluginId,
    @JsonProperty("versionConstraint") String versionConstraint
  ) {
    this.pluginId = pluginId;
    this.versionConstraint = versionConstraint;
  }

  public String getPluginId() {
    return pluginId;
  }

  public String getVersionConstraint() {
    return versionConstraint;
  }
}
