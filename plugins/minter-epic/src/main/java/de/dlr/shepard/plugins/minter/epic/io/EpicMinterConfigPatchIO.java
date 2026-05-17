package de.dlr.shepard.plugins.minter.epic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * KIP1c — request body for
 * {@code PATCH /v2/admin/minters/epic/config} (RFC 7396
 * merge-patch).
 *
 * <p>RFC 7396 semantics:
 *
 * <ul>
 *   <li>An absent field means "leave the current value alone".</li>
 *   <li>A {@code null} value means "remove / clear the field"
 *       (where the schema allows).</li>
 *   <li>A non-null value replaces the current value.</li>
 * </ul>
 *
 * <p>The boxed {@link Boolean} on {@link #enabled} carries the
 * absent-vs-present distinction for the toggle. String fields use
 * the {@code touched}-flag idiom (UH1a's pattern) to distinguish
 * "absent" from "explicit null/clear".
 *
 * <p>{@code credentialHash} / {@code credentialKey} are read-only via
 * this path — touching either raises
 * {@code minters.epic.config.read-only-field}. Operators set
 * credentials via {@code POST .../credential}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class EpicMinterConfigPatchIO {

  private Boolean enabled;

  private String apiBaseUrl;
  private boolean apiBaseUrlTouched;

  private String handlePrefix;
  private boolean handlePrefixTouched;

  /** Sentinel — caller mentioned credentialHash. Service rejects. */
  private boolean credentialHashTouched;
  /** Sentinel — caller mentioned credentialKey. Service rejects. */
  private boolean credentialKeyTouched;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public String getApiBaseUrl() {
    return apiBaseUrl;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setApiBaseUrl(String apiBaseUrl) {
    this.apiBaseUrl = apiBaseUrl;
    this.apiBaseUrlTouched = true;
  }

  public boolean isApiBaseUrlTouched() {
    return apiBaseUrlTouched;
  }

  public String getHandlePrefix() {
    return handlePrefix;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setHandlePrefix(String handlePrefix) {
    this.handlePrefix = handlePrefix;
    this.handlePrefixTouched = true;
  }

  public boolean isHandlePrefixTouched() {
    return handlePrefixTouched;
  }

  public boolean isCredentialHashTouched() {
    return credentialHashTouched;
  }

  public boolean isCredentialKeyTouched() {
    return credentialKeyTouched;
  }

  /** Catch-all setter — service rejects when the flag flips. */
  @JsonProperty("credentialHash")
  public void setCredentialHash(String ignored) {
    this.credentialHashTouched = true;
  }

  /** Catch-all setter — service rejects when the flag flips. */
  @JsonProperty("credentialKey")
  public void setCredentialKey(String ignored) {
    this.credentialKeyTouched = true;
  }

  /** Catch-all setter — service rejects via credentialKey's flag. */
  @JsonProperty("credential")
  public void setCredential(String ignored) {
    this.credentialKeyTouched = true;
  }
}
