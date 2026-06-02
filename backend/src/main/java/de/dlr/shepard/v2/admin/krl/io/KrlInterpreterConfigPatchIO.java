package de.dlr.shepard.v2.admin.krl.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * KRL-CONFIG-1 — request body for {@code PATCH /v2/admin/krl/config}
 * (RFC 7396 merge-patch).
 *
 * <p>Per RFC 7396:
 * <ul>
 *   <li>An absent field means "leave the current value alone".</li>
 *   <li>A {@code null} value on {@code sidecarUrl} means "clear the field"
 *       (revert to deploy-time default).</li>
 *   <li>Zero values on {@code timeoutSeconds} / {@code maxBodySizeMb}
 *       mean "revert to deploy-time default".</li>
 *   <li>A non-null/non-zero value replaces the current value.</li>
 * </ul>
 *
 * <p>All fields are genuinely tri-state (absent / null / present) so
 * each carries an explicit {@code *Touched} flag set by Jackson via
 * {@code @JsonSetter(nulls = SET)} to distinguish "absent" (leave
 * alone) from "explicit null" (clear to default) for String fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class KrlInterpreterConfigPatchIO {

  private Boolean enabled;
  private boolean enabledTouched;

  private String sidecarUrl;
  private boolean sidecarUrlTouched;

  private Integer timeoutSeconds;
  private boolean timeoutSecondsTouched;

  private Integer maxBodySizeMb;
  private boolean maxBodySizeMbTouched;

  public Boolean getEnabled() {
    return enabled;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code enabled} —
   * including the explicit-null case via {@link Nulls#SET}.
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
    this.enabledTouched = true;
  }

  public boolean isEnabledTouched() {
    return enabledTouched;
  }

  public String getSidecarUrl() {
    return sidecarUrl;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code sidecarUrl} —
   * including the explicit-null case via {@link Nulls#SET}.
   * A null on the wire means "clear the field" (revert to deploy-time
   * default at resolution time).
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setSidecarUrl(String sidecarUrl) {
    this.sidecarUrl = sidecarUrl;
    this.sidecarUrlTouched = true;
  }

  public boolean isSidecarUrlTouched() {
    return sidecarUrlTouched;
  }

  public Integer getTimeoutSeconds() {
    return timeoutSeconds;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code timeoutSeconds}.
   * Zero or null = "revert to deploy-time default".
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setTimeoutSeconds(Integer timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
    this.timeoutSecondsTouched = true;
  }

  public boolean isTimeoutSecondsTouched() {
    return timeoutSecondsTouched;
  }

  public Integer getMaxBodySizeMb() {
    return maxBodySizeMb;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code maxBodySizeMb}.
   * Zero or null = "revert to deploy-time default".
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setMaxBodySizeMb(Integer maxBodySizeMb) {
    this.maxBodySizeMb = maxBodySizeMb;
    this.maxBodySizeMbTouched = true;
  }

  public boolean isMaxBodySizeMbTouched() {
    return maxBodySizeMbTouched;
  }
}
