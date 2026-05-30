package de.dlr.shepard.v2.krl.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * KRL-CONFIG-1 — request body for
 * {@code PATCH /v2/admin/plugins/krl/config} (RFC 7396 merge-patch).
 *
 * <p>Per RFC 7396:
 * <ul>
 *   <li>An absent field means "leave the current value alone".</li>
 *   <li>A {@code null} value means "clear the field" (revert to the
 *       deploy-time default).</li>
 *   <li>A non-null value replaces the current value.</li>
 * </ul>
 *
 * <p>All three fields ({@link #sidecarUrl}, {@link #timeoutSeconds},
 * and {@link #maxBodySizeMb}) are genuinely tri-state —
 * absent/null/present — so each carries an explicit {@code *Touched}
 * flag set by Jackson via {@code @JsonSetter(nulls = SET)} to
 * distinguish "absent" (leave alone) from "explicit null" (clear to
 * default).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class KrlInterpreterConfigPatchIO {

  private String sidecarUrl;
  private boolean sidecarUrlTouched;

  private Integer timeoutSeconds;
  private boolean timeoutSecondsTouched;

  private Integer maxBodySizeMb;
  private boolean maxBodySizeMbTouched;

  public String getSidecarUrl() {
    return sidecarUrl;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code sidecarUrl} —
   * including the explicit-null case via {@link Nulls#SET}.
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
   * Jackson invokes this when the JSON body mentions {@code timeoutSeconds} —
   * including the explicit-null case via {@link Nulls#SET}.
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
   * Jackson invokes this when the JSON body mentions {@code maxBodySizeMb} —
   * including the explicit-null case via {@link Nulls#SET}.
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
