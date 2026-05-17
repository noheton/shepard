package de.dlr.shepard.v2.admin.sqltimeseries.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * P10c — request body for {@code PATCH /v2/admin/sql-timeseries/config}
 * (RFC 7396 merge-patch).
 *
 * <p>Per RFC 7396:
 * <ul>
 *   <li>An absent field means "leave the current value alone".</li>
 *   <li>A {@code null} value means "clear the field" (revert to deploy-time default).</li>
 *   <li>A non-null value replaces the current value.</li>
 * </ul>
 *
 * <p>Both fields ({@link #maxRows} and {@link #maxDurationIso}) are
 * genuinely tri-state — absent/null/present — so each carries an explicit
 * {@code *Touched} flag set by Jackson via {@code @JsonSetter(nulls = SET)}
 * to distinguish "absent" (leave alone) from "explicit null" (clear to default).
 *
 * <p>Wire name mapping:
 * <ul>
 *   <li>JSON key {@code "maxRows"} → {@link #maxRows} (Long)</li>
 *   <li>JSON key {@code "maxDuration"} → {@link #maxDurationIso} (String, ISO-8601)</li>
 * </ul>
 *
 * <p>Validation of values is done by the REST resource:
 * {@code maxRows} must be {@literal >} 0 when non-null;
 * {@code maxDuration} must be parseable by {@link java.time.Duration#parse} when non-null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SqlTimeseriesConfigPatchIO {

  private Long maxRows;
  private boolean maxRowsTouched;

  private String maxDurationIso;
  private boolean maxDurationTouched;

  public Long getMaxRows() {
    return maxRows;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code maxRows} —
   * including the explicit-null case via {@link Nulls#SET}.
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setMaxRows(Long maxRows) {
    this.maxRows = maxRows;
    this.maxRowsTouched = true;
  }

  public boolean isMaxRowsTouched() {
    return maxRowsTouched;
  }

  @JsonProperty("maxDuration")
  public String getMaxDurationIso() {
    return maxDurationIso;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code maxDuration} —
   * including the explicit-null case via {@link Nulls#SET}.
   * The wire name is {@code maxDuration}; stored internally as
   * {@code maxDurationIso} to avoid ambiguity with the entity field.
   */
  @JsonProperty("maxDuration")
  @JsonSetter(nulls = Nulls.SET)
  public void setMaxDurationIso(String maxDurationIso) {
    this.maxDurationIso = maxDurationIso;
    this.maxDurationTouched = true;
  }

  public boolean isMaxDurationTouched() {
    return maxDurationTouched;
  }
}
