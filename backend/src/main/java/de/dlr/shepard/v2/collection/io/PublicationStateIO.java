package de.dlr.shepard.v2.collection.io;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * #27-ARCHIVED — body shape for {@code PATCH /v2/collections/{appId}/publication-state}
 * and {@code PATCH /v2/containers/{appId}/publication-state}.
 *
 * <p>Single field {@code state} carrying one of the
 * {@code DRAFT | IN_REVIEW | READY | PUBLISHED | ARCHIVED} values. The
 * dedicated endpoint exists to apply a stricter auth gate than the generic
 * merge-patch flow: only the entity owner (Manage permission) or an
 * instance-admin may flip publication state to/from {@code ARCHIVED}.
 *
 * <p>The {@code archived} response field is convenience — equivalent to
 * {@code state == "ARCHIVED"} — and lets a frontend gate read-only UI
 * affordances without re-parsing the enum string.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PublicationState", description = "Lifecycle state flip for a Collection or Container.")
public class PublicationStateIO {

  @NotBlank
  @Schema(
    required = true,
    enumeration = {"DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED"},
    example = "ARCHIVED"
  )
  private String state;

  @Schema(readOnly = true, description = "True when state == ARCHIVED (frozen, prune-only).")
  private Boolean archived;

  public static PublicationStateIO of(String state) {
    return new PublicationStateIO(state, "ARCHIVED".equals(state));
  }
}
