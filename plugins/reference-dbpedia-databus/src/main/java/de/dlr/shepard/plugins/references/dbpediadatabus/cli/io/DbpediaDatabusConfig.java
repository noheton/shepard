package de.dlr.shepard.plugins.references.dbpediadatabus.cli.io;

import java.util.Date;
import java.util.List;

/**
 * REF1c — CLI mirror of the backend's {@code DbpediaDatabusConfigIO}
 * JSON shape. Read-side only; PATCH requests are sent as raw
 * {@code Map<String, Object>} bodies.
 */
public final class DbpediaDatabusConfig {

  private boolean enabled;
  private String defaultEndpoint;
  private List<String> allowedHosts;
  private long cacheTtlSeconds;
  private String authMode;
  private String oauthTokenUrl;
  private String oauthClientId;
  private boolean oauthClientSecretSet;
  private String oauthClientSecretFingerprint;
  private Date updatedAt;
  private String updatedBy;

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getDefaultEndpoint() { return defaultEndpoint; }
  public void setDefaultEndpoint(String defaultEndpoint) { this.defaultEndpoint = defaultEndpoint; }
  public List<String> getAllowedHosts() { return allowedHosts; }
  public void setAllowedHosts(List<String> allowedHosts) { this.allowedHosts = allowedHosts; }
  public long getCacheTtlSeconds() { return cacheTtlSeconds; }
  public void setCacheTtlSeconds(long cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }
  public String getAuthMode() { return authMode; }
  public void setAuthMode(String authMode) { this.authMode = authMode; }
  public String getOauthTokenUrl() { return oauthTokenUrl; }
  public void setOauthTokenUrl(String oauthTokenUrl) { this.oauthTokenUrl = oauthTokenUrl; }
  public String getOauthClientId() { return oauthClientId; }
  public void setOauthClientId(String oauthClientId) { this.oauthClientId = oauthClientId; }
  public boolean isOauthClientSecretSet() { return oauthClientSecretSet; }
  public void setOauthClientSecretSet(boolean oauthClientSecretSet) { this.oauthClientSecretSet = oauthClientSecretSet; }
  public String getOauthClientSecretFingerprint() { return oauthClientSecretFingerprint; }
  public void setOauthClientSecretFingerprint(String f) { this.oauthClientSecretFingerprint = f; }
  public Date getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
  public String getUpdatedBy() { return updatedBy; }
  public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
