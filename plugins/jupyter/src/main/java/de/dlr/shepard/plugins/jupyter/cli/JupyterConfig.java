package de.dlr.shepard.plugins.jupyter.cli;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * J1e — CLI-side mirror of {@code JupyterConfigIO} from
 * {@code GET/PATCH /v2/admin/plugins/jupyter/config}.
 *
 * <p>{@code enabled} is the master switch for the "Open in JupyterHub"
 * affordance; {@code hubUrl} is the base URL the affordance targets.
 * Both fields are surfaced as nullable on the wire (hubUrl) or
 * primitive boolean (enabled) — see {@code aidocs/16-dispatcher-backlog.md}
 * J1e row.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class JupyterConfig {

  private final boolean enabled;
  private final String hubUrl;

  public JupyterConfig(
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("hubUrl") String hubUrl
  ) {
    this.enabled = enabled;
    this.hubUrl = hubUrl;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getHubUrl() {
    return hubUrl;
  }
}
