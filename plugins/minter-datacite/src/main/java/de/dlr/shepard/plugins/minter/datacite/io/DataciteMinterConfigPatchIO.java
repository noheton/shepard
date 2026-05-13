package de.dlr.shepard.plugins.minter.datacite.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * KIP1d — request body for
 * {@code PATCH /v2/admin/minters/datacite/config} (RFC 7396
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
 * <p>{@code passwordHash} / {@code passwordCipher} are read-only via
 * this path — touching either raises
 * {@code minters.datacite.config.read-only-field}. Operators set
 * credentials via {@code POST .../credential}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class DataciteMinterConfigPatchIO {

  private Boolean enabled;

  private String apiBaseUrl;
  private boolean apiBaseUrlTouched;

  private String handlePrefix;
  private boolean handlePrefixTouched;

  private String repositoryId;
  private boolean repositoryIdTouched;

  private String publisher;
  private boolean publisherTouched;

  private String landingPageBase;
  private boolean landingPageBaseTouched;

  private String defaultState;
  private boolean defaultStateTouched;

  /** Sentinel — caller mentioned passwordHash. Service rejects. */
  private boolean passwordHashTouched;
  /** Sentinel — caller mentioned passwordCipher. Service rejects. */
  private boolean passwordCipherTouched;

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

  public String getRepositoryId() {
    return repositoryId;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setRepositoryId(String repositoryId) {
    this.repositoryId = repositoryId;
    this.repositoryIdTouched = true;
  }

  public boolean isRepositoryIdTouched() {
    return repositoryIdTouched;
  }

  public String getPublisher() {
    return publisher;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setPublisher(String publisher) {
    this.publisher = publisher;
    this.publisherTouched = true;
  }

  public boolean isPublisherTouched() {
    return publisherTouched;
  }

  public String getLandingPageBase() {
    return landingPageBase;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setLandingPageBase(String landingPageBase) {
    this.landingPageBase = landingPageBase;
    this.landingPageBaseTouched = true;
  }

  public boolean isLandingPageBaseTouched() {
    return landingPageBaseTouched;
  }

  public String getDefaultState() {
    return defaultState;
  }

  @JsonSetter(nulls = Nulls.SET)
  public void setDefaultState(String defaultState) {
    this.defaultState = defaultState;
    this.defaultStateTouched = true;
  }

  public boolean isDefaultStateTouched() {
    return defaultStateTouched;
  }

  public boolean isPasswordHashTouched() {
    return passwordHashTouched;
  }

  public boolean isPasswordCipherTouched() {
    return passwordCipherTouched;
  }

  /** Catch-all setter — service rejects when the flag flips. */
  @JsonProperty("passwordHash")
  public void setPasswordHash(String ignored) {
    this.passwordHashTouched = true;
  }

  /** Catch-all setter — service rejects when the flag flips. */
  @JsonProperty("passwordCipher")
  public void setPasswordCipher(String ignored) {
    this.passwordCipherTouched = true;
  }

  /** Catch-all setter — service rejects via passwordCipher's flag. */
  @JsonProperty("password")
  public void setPassword(String ignored) {
    this.passwordCipherTouched = true;
  }
}
