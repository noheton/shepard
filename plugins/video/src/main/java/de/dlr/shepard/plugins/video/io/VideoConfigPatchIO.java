package de.dlr.shepard.plugins.video.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/**
 * VID1c — request body for {@code PATCH /v2/admin/video/config}
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
 * <p>{@code ffprobeEnabled} uses a boxed {@code Boolean} so absent
 * maps to {@code null} (= leave alone); present-true / present-false
 * replaces. Explicit-null on this toggle is semantically meaningless
 * and the service ignores it (keeps existing).
 *
 * <p>{@code maxFileSizeMb} is genuinely tri-state — absent (leave
 * alone), explicit-null (clear the cap = unlimited), explicit-Long
 * (set the new cap). Tracking the absent-vs-null distinction requires
 * the explicit {@code maxFileSizeMbTouched} flag set by Jackson via
 * the {@code @JsonSetter(nulls=SET)} on the property setter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class VideoConfigPatchIO {

  private Boolean ffprobeEnabled;
  private Long maxFileSizeMb;

  /**
   * Tracks whether the JSON body mentioned the {@code maxFileSizeMb}
   * field at all (regardless of its value). When {@code true},
   * the patch will apply the value (which may be {@code null} =
   * clear the cap).
   */
  private boolean maxFileSizeMbTouched;

  public Boolean getFfprobeEnabled() {
    return ffprobeEnabled;
  }

  public void setFfprobeEnabled(Boolean ffprobeEnabled) {
    this.ffprobeEnabled = ffprobeEnabled;
  }

  public Long getMaxFileSizeMb() {
    return maxFileSizeMb;
  }

  /**
   * Jackson calls this setter when the JSON body mentions
   * {@code maxFileSizeMb} — including the explicit-null case via
   * {@link Nulls#SET}. We capture both the value and the
   * "field-was-present" bit so the service can distinguish "absent"
   * (leave alone) from "explicit null" (clear the cap).
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setMaxFileSizeMb(Long maxFileSizeMb) {
    this.maxFileSizeMb = maxFileSizeMb;
    this.maxFileSizeMbTouched = true;
  }

  public boolean isMaxFileSizeMbTouched() {
    return maxFileSizeMbTouched;
  }
}
