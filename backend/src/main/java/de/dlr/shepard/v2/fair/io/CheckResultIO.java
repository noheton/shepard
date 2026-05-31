package de.dlr.shepard.v2.fair.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * FAIR4 — one check-row in a {@link MetadataCompletenessScoreIO} response.
 *
 * <p>The {@code checkId} mirrors the TypeScript {@code MetadataCheckId} union
 * in {@code frontend/utils/metadataCompleteness.ts} so that the UI can
 * reconcile server-side and client-side check outcomes without a mapping table.
 *
 * <p>Possible {@code checkId} values (must stay in sync with the TS type):
 * {@code name}, {@code description}, {@code license}, {@code accessRights},
 * {@code creatorOrcid}, {@code semanticAnnotation}, {@code labJournal},
 * {@code keywords}, {@code dataObjects}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CheckResult")
public class CheckResultIO {

  @Schema(
    description = "Stable identifier for this check — mirrors the TypeScript MetadataCheckId union " +
      "in frontend/utils/metadataCompleteness.ts. Values: name, description, license, " +
      "accessRights, creatorOrcid, semanticAnnotation, labJournal, keywords, dataObjects.",
    readOnly = true,
    example = "license"
  )
  private String checkId;

  @Schema(
    description = "Short human-readable label for this check.",
    readOnly = true,
    example = "License (SPDX) set"
  )
  private String label;

  @Schema(
    description = "True when the check passes; false when the field/condition is missing.",
    readOnly = true
  )
  private boolean passed;

  @Schema(
    description = "Points this check contributes to the score when it passes.",
    readOnly = true,
    example = "20"
  )
  private int weight;

  @Schema(
    description = "One-line hint explaining why this check matters (FAIR / funder mapping). " +
      "Null when not applicable.",
    readOnly = true,
    nullable = true,
    example = "DataCite §16 (Rights) + F-UJI FsF-R1.1-01M — the single biggest blocker to publication."
  )
  private String hint;
}
