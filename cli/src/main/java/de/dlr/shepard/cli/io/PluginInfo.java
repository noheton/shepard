package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Wire-shape mirror of the backend's
 * {@code de.dlr.shepard.v2.admin.plugins.io.PluginEntryIO}.
 *
 * <p>Decoupled from the backend module so the CLI does not pull in
 * Quarkus / JAX-RS just for one DTO — same pattern as
 * {@link FeatureToggle} and the rest of the read-side CLI IO
 * records. (Plugin-side wire mirrors, e.g. unhide's
 * {@code UnhideConfig}, live in their plugin module's
 * {@code plugins/<id>/.../cli/io/} package post-PM1d.)
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
  // PM1c — enriched manifest metadata. All nullable / optional so a
  // backend running ahead of the CLI doesn't break the read path.
  private final String title;
  private final String description;
  private final String homepageUrl;
  private final String repositoryUrl;
  private final String licence;
  private final List<PluginDependency> dependencies;

  public PluginInfo(
    @JsonProperty("id") String id,
    @JsonProperty("version") String version,
    @JsonProperty("shepardCompatibility") String shepardCompatibility,
    @JsonProperty("state") String state,
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("sourcePath") String sourcePath,
    @JsonProperty("registeredAt") Date registeredAt,
    @JsonProperty("failureMessage") String failureMessage,
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("homepageUrl") String homepageUrl,
    @JsonProperty("repositoryUrl") String repositoryUrl,
    @JsonProperty("licence") String licence,
    @JsonProperty("dependencies") List<PluginDependency> dependencies
  ) {
    this.id = id;
    this.version = version;
    this.shepardCompatibility = shepardCompatibility;
    this.state = state;
    this.enabled = enabled;
    this.sourcePath = sourcePath;
    this.registeredAt = registeredAt;
    this.failureMessage = failureMessage;
    this.title = title;
    this.description = description;
    this.homepageUrl = homepageUrl;
    this.repositoryUrl = repositoryUrl;
    this.licence = licence;
    this.dependencies = dependencies == null ? Collections.emptyList() : dependencies;
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

  /** PM1c — human-readable display name (falls back to {@link #getId()}). */
  public String getTitle() {
    return title;
  }

  /** PM1c — one-paragraph operator-facing description. */
  public String getDescription() {
    return description;
  }

  /** PM1c — homepage URL, or {@code null}. */
  public String getHomepageUrl() {
    return homepageUrl;
  }

  /** PM1c — source-code repository URL, or {@code null}. */
  public String getRepositoryUrl() {
    return repositoryUrl;
  }

  /** PM1c — SPDX licence id, or {@code null}. */
  public String getLicence() {
    return licence;
  }

  /** PM1c — declared dependencies (never null). */
  public List<PluginDependency> getDependencies() {
    return dependencies;
  }
}
