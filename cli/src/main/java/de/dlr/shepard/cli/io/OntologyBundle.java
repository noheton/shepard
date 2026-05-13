package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire-shape mirror of the backend's {@code OntologyBundleIO} (lives
 * in {@code de.dlr.shepard.v2.admin.semantic.io}). Decoupled so the
 * CLI module doesn't pull the Quarkus stack just for the DTO.
 *
 * <p>One row in {@code GET /v2/admin/semantic/ontologies}, or the body
 * returned by {@code POST /v2/admin/semantic/ontologies/{id}/enable
 * |/disable} and {@code POST /v2/admin/semantic/ontologies} (upload).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class OntologyBundle {

  private final String id;
  private final String name;
  private final String source; // "builtin" | "user"
  private final boolean required;
  private final boolean enabled;
  private final String iriPrefix;
  private final String canonicalUrl;
  private final String license;
  private final String sha256;
  private final long byteSize;

  @JsonCreator
  public OntologyBundle(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("source") String source,
    @JsonProperty("required") boolean required,
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("iriPrefix") String iriPrefix,
    @JsonProperty("canonicalUrl") String canonicalUrl,
    @JsonProperty("license") String license,
    @JsonProperty("sha256") String sha256,
    @JsonProperty("byteSize") long byteSize
  ) {
    this.id = id;
    this.name = name;
    this.source = source;
    this.required = required;
    this.enabled = enabled;
    this.iriPrefix = iriPrefix;
    this.canonicalUrl = canonicalUrl;
    this.license = license;
    this.sha256 = sha256;
    this.byteSize = byteSize;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSource() {
    return source;
  }

  public boolean isRequired() {
    return required;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getIriPrefix() {
    return iriPrefix;
  }

  public String getCanonicalUrl() {
    return canonicalUrl;
  }

  public String getLicense() {
    return license;
  }

  public String getSha256() {
    return sha256;
  }

  public long getByteSize() {
    return byteSize;
  }
}
