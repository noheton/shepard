package de.dlr.shepard.v2.dataobject.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PROV1k — read-side typed predecessor entry in
 * {@link DataObjectDetailV2IO#getTypedPredecessorSummaries()}.
 *
 * <p>Pairs the compact predecessor summary (appId, id, name, status) with
 * the stored PROV-O / FAIR²R relationship type. Pre-PROV1k DataObjects
 * (whose {@code typedPredecessorsJson} is null) emit
 * {@code "prov:wasInformedBy"} as the default.
 */
@Schema(
  name = "TypedPredecessorSummary",
  description =
    "PROV1k read-side — compact predecessor DataObject summary plus the " +
    "PROV-O / FAIR²R relationship type under which it was linked. " +
    "relationshipType is always non-null; defaults to 'prov:wasInformedBy' " +
    "for predecessors created before PROV1k."
)
public record TypedPredecessorSummaryIO(

  @Schema(readOnly = true, description = "appId (UUID v7) of the predecessor DataObject.")
  String predecessorAppId,

  @Schema(readOnly = true, description = "Numeric shepardId of the predecessor DataObject.")
  long predecessorId,

  @Schema(readOnly = true, description = "Display name of the predecessor DataObject.")
  String predecessorName,

  @Schema(readOnly = true, nullable = true, description = "Lifecycle status of the predecessor DataObject.")
  String predecessorStatus,

  @Schema(
    readOnly = true,
    description =
      "PROV-O or FAIR²R relationship type: " +
      "'prov:wasInformedBy' (default / generic), " +
      "'prov:wasRevisionOf' (direct revision/correction), " +
      "'fair2r:repairs' (rework/NCR-repair).",
    enumeration = {"prov:wasInformedBy", "prov:wasRevisionOf", "fair2r:repairs"},
    defaultValue = "prov:wasInformedBy"
  )
  String relationshipType

) {}
