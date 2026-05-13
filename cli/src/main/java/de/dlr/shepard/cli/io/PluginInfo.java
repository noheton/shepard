package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

/**
 * Wire-shape mirror of the backend's
 * {@code de.dlr.shepard.v2.admin.plugins.io.PluginEntryIO}.
 *
 * <p>Decoupled from the backend module so the CLI does not pull in
 * Quarkus / JAX-RS just for one DTO — same pattern as
 * {@link FeatureToggle}, {@link UnhideConfig}, and the rest of the
 * read-side CLI IO records.
 *
 * <p>Unknown fields are ignored so a backend running ahead of this
 * CLI's view of the schema doesn't fail deserialisation; the table
 * formatter only renders the columns it knows about.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PluginInfo {

  private final String id;
  private final String version;
  private final String shepardCompatibility;
  private final String state;
  private final boolean enabled;
  private final String sourcePath;
  private final Date registeredAt;
  private final String failureMessage;

  public PluginInfo(
    @JsonProperty("id") String id,
    @JsonProperty("version") String version,
    @JsonProperty("shepardCompatibility") String shepardCompatibility,
    @JsonProperty("state") String state,
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("sourcePath") String sourcePath,
    @JsonProperty("registeredAt") Date registeredAt,
    @JsonProperty("failureMessage") String failureMessage
  ) {
    this.id = id;
    this.version = version;
    this.shepardCompatibility = shepardCompatibility;
    this.state = state;
    this.enabled = enabled;
    this.sourcePath = sourcePath;
    this.registeredAt = registeredAt;
    this.failureMessage = failureMessage;
  }

  public String getId() {
    return id;
  }

  public String getVersion() {
    return version;
  }

  public String getShepardCompatibility() {
    return shepardCompatibility;
  }

  public String getState() {
    return state;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getSourcePath() {
    return sourcePath;
  }

  public Date getRegisteredAt() {
    return registeredAt;
  }

  public String getFailureMessage() {
    return failureMessage;
  }
}
