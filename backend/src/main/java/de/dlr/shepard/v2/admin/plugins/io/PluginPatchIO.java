package de.dlr.shepard.v2.admin.plugins.io;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashSet;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PM1b — request body for {@code PATCH /v2/admin/plugins/{id}}
 * (RFC 7396 merge-patch).
 *
 * <p>Per RFC 7396 a JSON merge-patch's semantics are:
 *
 * <ul>
 *   <li>An absent field means "leave the current value alone".</li>
 *   <li>A {@code null} value means "remove / clear the field"
 *       (where the schema allows — not applicable for the boolean
 *       {@code enabled} toggle).</li>
 *   <li>A non-null value replaces the current value.</li>
 * </ul>
 *
 * <p>Field set: only {@code enabled} is patchable today. Any other
 * field mentioned in the body is captured via {@link JsonAnySetter}
 * and surfaced through {@link #unknownFields()} so the resource can
 * reject the request with {@code plugin.config.read-only-field}
 * (defensive against future field accretion / typos).
 *
 * <p>The {@code Boolean}-boxed {@link #enabled} field preserves the
 * "absent" vs "explicit true / false" distinction Jackson needs — an
 * absent field deserialises to {@code null} (= no flip requested);
 * any explicit boolean drives the flip. The resource rejects an
 * empty patch (all fields absent) as a no-op responding 200 with the
 * current entry (matching A3b's "no body, same shape" idiom).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PluginPatch", description = "RFC 7396 merge-patch body for a plugin entry.")
public final class PluginPatchIO {

  private Boolean enabled;

  /**
   * Captures any extra fields a misguided caller might include —
   * surfaced through {@link #unknownFields()} so the resource can
   * raise {@code plugin.config.read-only-field}. Names are
   * insertion-ordered so the error response can cite the first
   * offending field deterministically.
   */
  private final Set<String> unknownFields = new LinkedHashSet<>();

  @Schema(description = "Desired enabled toggle for the plugin. Absent = leave alone.")
  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  @JsonAnySetter
  public void captureUnknown(String name, Object value) {
    unknownFields.add(name);
  }

  /** Read-side accessor for the resource layer's defensive guard. */
  public Set<String> unknownFields() {
    return Set.copyOf(unknownFields);
  }
}
