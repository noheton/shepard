package de.dlr.shepard.plugins.aas.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * AAS1l — request body for {@code PATCH /v2/admin/aas/config}
 * (RFC 7396 merge-patch).
 *
 * <p>Per RFC 7396 a JSON merge-patch's semantics are:
 *
 * <ul>
 *   <li>An absent field means "leave the current value alone".</li>
 *   <li>A {@code null} value means "remove / clear the field"
 *       (where the schema allows).</li>
 *   <li>A non-null value replaces the current value.</li>
 * </ul>
 *
 * <p>To preserve the "absent vs explicit-null" distinction Jackson
 * needs the boxed {@code Boolean} on {@link #enabled}; absent maps to
 * {@code null} (= leave alone), present-true / present-false replaces.
 *
 * <p>{@code registryUrl} and {@code baseUrl} are genuinely tri-state —
 * absent (leave alone), explicit-null (clear), explicit-string
 * (replace). The {@code *Touched} flags set by Jackson via
 * {@code @JsonSetter(nulls=SET)} on the property setters let the
 * service distinguish "absent" from "explicit null" (clear).
 *
 * <p>{@code registryApiKey} is also tri-state: absent (leave alone),
 * explicit-null (clear/revoke), explicit-string (set new key).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AasConfigPatchIO {

  private Boolean enabled;
  private String registryUrl;
  private String registryApiKey;
  private String baseUrl;

  /**
   * Tracks whether the JSON body mentioned {@code registryUrl}
   * at all (regardless of its value). When {@code true},
   * the patch will apply the value (which may be {@code null} = clear).
   */
  private boolean registryUrlTouched;

  /**
   * Tracks whether the JSON body mentioned {@code registryApiKey}
   * at all (regardless of its value).
   */
  private boolean registryApiKeyTouched;

  /**
   * Tracks whether the JSON body mentioned {@code baseUrl}
   * at all (regardless of its value).
   */
  private boolean baseUrlTouched;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public String getRegistryUrl() {
    return registryUrl;
  }

  /**
   * Jackson calls this setter when the JSON body mentions
   * {@code registryUrl} — including the explicit-null case via
   * {@link Nulls#SET}. We capture both the value and the
   * "field-was-present" bit so the service can distinguish
   * "absent" (leave alone) from "explicit null" (clear).
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setRegistryUrl(String registryUrl) {
    this.registryUrl = registryUrl;
    this.registryUrlTouched = true;
  }

  public String getRegistryApiKey() {
    return registryApiKey;
  }

  /**
   * Jackson calls this setter when the JSON body mentions
   * {@code registryApiKey} — including explicit-null (= revoke).
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setRegistryApiKey(String registryApiKey) {
    this.registryApiKey = registryApiKey;
    this.registryApiKeyTouched = true;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  /**
   * Jackson calls this setter when the JSON body mentions
   * {@code baseUrl} — including explicit-null (= clear).
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
    this.baseUrlTouched = true;
  }

  public boolean isRegistryUrlTouched() {
    return registryUrlTouched;
  }

  public boolean isRegistryApiKeyTouched() {
    return registryApiKeyTouched;
  }

  public boolean isBaseUrlTouched() {
    return baseUrlTouched;
  }
}
