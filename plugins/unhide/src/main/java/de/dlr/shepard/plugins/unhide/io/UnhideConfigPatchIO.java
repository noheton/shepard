package de.dlr.shepard.plugins.unhide.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * UH1a — request body for {@code PATCH /v2/admin/unhide/config}
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
 * needs the boxed {@code Boolean} on {@link #enabled} +
 * {@link #feedPublic}; absent maps to {@code null} (= leave alone),
 * present-true / present-false replaces. We disallow explicit-null
 * on those two because a {@code null} boolean toggle is semantically
 * meaningless — the service rejects such a patch via Bean Validation
 * if it ever happens.
 *
 * <p>{@code contactEmail} is genuinely tri-state — absent (leave
 * alone), explicit-null (clear), explicit-string (replace). Tracking
 * the absent-vs-null distinction requires the explicit
 * {@code contactEmailTouched} flag set by Jackson via the
 * {@code @JsonSetter(nulls=SET)} on the property setter.
 *
 * <p>{@code harvestApiKeyHash} is read-only via this path —
 * mentioning it in a patch body raises
 * {@code unhide.config.read-only-field}. Operators rotate via
 * {@code POST /v2/admin/unhide/harvest-key/rotate}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class UnhideConfigPatchIO {

  private Boolean enabled;
  private Boolean feedPublic;
  private String contactEmail;

  /**
   * Tracks whether the JSON body mentioned the {@code contactEmail}
   * field at all (regardless of its value). When {@code true},
   * the patch will apply the value (which may be {@code null} =
   * clear).
   */
  private boolean contactEmailTouched;

  /**
   * Sentinel — when {@code true}, the caller tried to PATCH the
   * read-only hash. Service rejects with 400
   * {@code unhide.config.read-only-field}.
   */
  private boolean harvestApiKeyHashTouched;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getFeedPublic() {
    return feedPublic;
  }

  public void setFeedPublic(Boolean feedPublic) {
    this.feedPublic = feedPublic;
  }

  public String getContactEmail() {
    return contactEmail;
  }

  /**
   * Jackson calls this setter when the JSON body mentions
   * {@code contactEmail} — including the explicit-null case via
   * {@link Nulls#SET}. We capture both the value and the
   * "field-was-present" bit so the service can distinguish "absent"
   * (leave alone) from "explicit null" (clear).
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setContactEmail(String contactEmail) {
    this.contactEmail = contactEmail;
    this.contactEmailTouched = true;
  }

  public boolean isContactEmailTouched() {
    return contactEmailTouched;
  }

  public boolean isHarvestApiKeyHashTouched() {
    return harvestApiKeyHashTouched;
  }

  /**
   * Catch-all setter for the read-only {@code harvestApiKeyHash}
   * field — Jackson invokes this when a caller includes the field
   * in the patch body (even with a {@code null} value), and the
   * service rejects the patch on the resulting flag. We accept the
   * property under both {@code harvestApiKeyHash} and the camelCase
   * variant.
   */
  @JsonProperty("harvestApiKeyHash")
  public void setHarvestApiKeyHash(String ignored) {
    this.harvestApiKeyHashTouched = true;
  }
}
