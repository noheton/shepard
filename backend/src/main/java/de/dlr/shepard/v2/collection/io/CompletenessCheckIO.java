package de.dlr.shepard.v2.collection.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * RDM-005a(d) — one entry in the per-check breakdown returned by
 * {@code GET /v2/collections/{appId}/completeness}.
 *
 * <p>The {@code id} field is the stable slug used by the client-side
 * {@code MetadataCheckId} TypeScript union in
 * {@code frontend/utils/metadataCompleteness.ts}. Keeping both lists
 * in sync is enforced by the shared 9-check contract.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CompletenessCheck")
public class CompletenessCheckIO {

  @Schema(
    description = "Stable identifier slug — matches MetadataCheckId in metadataCompleteness.ts.",
    example = "license",
    enumeration = {
      "name", "description", "license", "accessRights",
      "creatorOrcid", "semanticAnnotation", "labJournal", "keywords", "dataObjects"
    }
  )
  private String id;

  @Schema(
    description = "Short human-readable label for the check.",
    example = "License (SPDX) set"
  )
  private String label;

  @Schema(
    description = "True when the check passes; false when the field/condition is absent.",
    example = "false"
  )
  private boolean passed;

  @Schema(
    description = "Points awarded when this check passes (added to score when passed=true).",
    example = "20"
  )
  private int points;
}
