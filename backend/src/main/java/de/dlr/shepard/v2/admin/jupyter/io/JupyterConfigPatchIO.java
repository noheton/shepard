package de.dlr.shepard.v2.admin.jupyter.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * J1e — request body for {@code PATCH /v2/admin/jupyter/config}
 * (RFC 7396 merge-patch).
 *
 * <p>Per RFC 7396:
 * <ul>
 *   <li>An absent field means "leave the current value alone".</li>
 *   <li>A {@code null} value means "clear the field" (revert to
 *       deploy-time default for {@code hubUrl}; for {@code enabled}
 *       an explicit null is treated as "leave alone" by the REST
 *       layer since the field is a non-null boolean).</li>
 *   <li>A non-null value replaces the current value.</li>
 * </ul>
 *
 * <p>Both fields ({@link #enabled} and {@link #hubUrl}) are genuinely
 * tri-state — absent/null/present — so each carries an explicit
 * {@code *Touched} flag set by Jackson via
 * {@code @JsonSetter(nulls = SET)} to distinguish "absent" (leave
 * alone) from "explicit null" (clear to default).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JupyterConfigPatchIO {

  private Boolean enabled;
  private boolean enabledTouched;

  private String hubUrl;
  private boolean hubUrlTouched;

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

  public String getHubUrl() {
    return hubUrl;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code hubUrl} —
   * including the explicit-null case via {@link Nulls#SET}.
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setHubUrl(String hubUrl) {
    this.hubUrl = hubUrl;
    this.hubUrlTouched = true;
  }

  public boolean isHubUrlTouched() {
    return hubUrlTouched;
  }
}
