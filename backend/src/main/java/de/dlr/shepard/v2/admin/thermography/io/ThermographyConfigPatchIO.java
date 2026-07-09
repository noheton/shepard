package de.dlr.shepard.v2.admin.thermography.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * MFFD-NDT-ADMIN-CONFIG-1 — request body for
 * {@code PATCH /v2/admin/thermography/config} (RFC 7396 merge-patch).
 *
 * <p>Per RFC 7396:
 * <ul>
 *   <li>An absent field means "leave the current value alone".</li>
 *   <li>A {@code null} value means "clear the field" (revert to the
 *       deploy-time default for that knob).</li>
 *   <li>A non-null value replaces the current value.</li>
 * </ul>
 *
 * <p>All three fields are genuinely tri-state (absent/null/present),
 * so each carries an explicit {@code *Touched} flag set by Jackson via
 * {@code @JsonSetter(nulls = SET)} to distinguish "absent" (leave alone)
 * from "explicit null" (revert to deploy-time default).
 */
@Schema(description = "RFC 7396 merge-patch body for PATCH /v2/admin/thermography/config.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ThermographyConfigPatchIO {

  private Double thresholdC;
  private boolean thresholdCTouched;

  private Integer gridWidth;
  private boolean gridWidthTouched;

  private Integer gridHeight;
  private boolean gridHeightTouched;

  public Double getThresholdC() {
    return thresholdC;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code thresholdC} —
   * including the explicit-null case via {@link Nulls#SET}.
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setThresholdC(Double thresholdC) {
    this.thresholdC = thresholdC;
    this.thresholdCTouched = true;
  }

  public boolean isThresholdCTouched() {
    return thresholdCTouched;
  }

  public Integer getGridWidth() {
    return gridWidth;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code gridWidth} —
   * including the explicit-null case via {@link Nulls#SET}.
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setGridWidth(Integer gridWidth) {
    this.gridWidth = gridWidth;
    this.gridWidthTouched = true;
  }

  public boolean isGridWidthTouched() {
    return gridWidthTouched;
  }

  public Integer getGridHeight() {
    return gridHeight;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code gridHeight} —
   * including the explicit-null case via {@link Nulls#SET}.
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setGridHeight(Integer gridHeight) {
    this.gridHeight = gridHeight;
    this.gridHeightTouched = true;
  }

  public boolean isGridHeightTouched() {
    return gridHeightTouched;
  }
}
