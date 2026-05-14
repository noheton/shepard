package de.dlr.shepard.plugins.references.dbpediadatabus.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.List;

/**
 * REF1c — request body for
 * {@code PATCH /v2/admin/references/dbpedia-databus/config}
 * (RFC 7396 merge-patch).
 *
 * <p>Tri-state nullable fields use {@code @JsonSetter(nulls=SET)}:
 * Jackson invokes the setter regardless of value (including
 * explicit-null), so we can flag "field was present in the body"
 * via the matching {@code Touched} boolean.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class DbpediaDatabusConfigPatchIO {

  private Boolean enabled;
  private Long cacheTtlSeconds;
  private String authMode;

  private String defaultEndpoint;
  private boolean defaultEndpointTouched;

  private List<String> allowedHosts;
  private boolean allowedHostsTouched;

  private String oauthTokenUrl;
  private boolean oauthTokenUrlTouched;

  private String oauthClientId;
  private boolean oauthClientIdTouched;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Long getCacheTtlSeconds() {
    return cacheTtlSeconds;
  }

  public void setCacheTtlSeconds(Long cacheTtlSeconds) {
    this.cacheTtlSeconds = cacheTtlSeconds;
  }

  public String getAuthMode() {
    return authMode;
  }

  public void setAuthMode(String authMode) {
    this.authMode = authMode;
  }

  public String getDefaultEndpoint() {
    return defaultEndpoint;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setDefaultEndpoint(String defaultEndpoint) {
    this.defaultEndpoint = defaultEndpoint;
    this.defaultEndpointTouched = true;
  }

  public boolean isDefaultEndpointTouched() {
    return defaultEndpointTouched;
  }

  public List<String> getAllowedHosts() {
    return allowedHosts;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setAllowedHosts(List<String> allowedHosts) {
    this.allowedHosts = allowedHosts;
    this.allowedHostsTouched = true;
  }

  public boolean isAllowedHostsTouched() {
    return allowedHostsTouched;
  }

  public String getOauthTokenUrl() {
    return oauthTokenUrl;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setOauthTokenUrl(String oauthTokenUrl) {
    this.oauthTokenUrl = oauthTokenUrl;
    this.oauthTokenUrlTouched = true;
  }

  public boolean isOauthTokenUrlTouched() {
    return oauthTokenUrlTouched;
  }

  public String getOauthClientId() {
    return oauthClientId;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setOauthClientId(String oauthClientId) {
    this.oauthClientId = oauthClientId;
    this.oauthClientIdTouched = true;
  }

  public boolean isOauthClientIdTouched() {
    return oauthClientIdTouched;
  }
}
