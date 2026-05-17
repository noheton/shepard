package de.dlr.shepard.v2.admin.ror.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * ROR1 — request body for {@code PATCH /v2/admin/instance/ror}
 * (RFC 7396 merge-patch).
 *
 * <p>Per RFC 7396:
 * <ul>
 *   <li>An absent field means "leave the current value alone".</li>
 *   <li>A {@code null} value means "clear the field".</li>
 *   <li>A non-null value replaces the current value.</li>
 * </ul>
 *
 * <p>Both fields ({@link #rorId} and {@link #organizationName}) are
 * genuinely tri-state — absent/null/present — so each carries an
 * explicit {@code *Touched} flag set by Jackson via
 * {@code @JsonSetter(nulls = SET)} to distinguish "absent" (leave
 * alone) from "explicit null" (clear).
 *
 * <p>Validation of {@code rorId} is done by the REST resource: must
 * match {@code [A-Za-z0-9]{1,9}} when non-null/non-blank.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InstanceRorConfigPatchIO {

  private String rorId;
  private boolean rorIdTouched;

  private String organizationName;
  private boolean organizationNameTouched;

  public String getRorId() {
    return rorId;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code rorId} —
   * including the explicit-null case via {@link Nulls#SET}.
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setRorId(String rorId) {
    this.rorId = rorId;
    this.rorIdTouched = true;
  }

  public boolean isRorIdTouched() {
    return rorIdTouched;
  }

  public String getOrganizationName() {
    return organizationName;
  }

  /**
   * Jackson invokes this when the JSON body mentions
   * {@code organizationName} — including the explicit-null case via
   * {@link Nulls#SET}.
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setOrganizationName(String organizationName) {
    this.organizationName = organizationName;
    this.organizationNameTouched = true;
  }

  public boolean isOrganizationNameTouched() {
    return organizationNameTouched;
  }
}
