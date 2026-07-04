package de.dlr.shepard.v2.dataobject.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * QM1b — request body for
 * {@code PATCH /v2/collections/{cid}/data-objects/{did}/predecessors/{predAppId}}.
 *
 * <p>Single-field body that sets / replaces the PROV-O / FAIR²R relationship
 * type for an already-linked predecessor edge. Accepted values mirror
 * {@link TypedPredecessorIO#ALLOWED_TYPES} — currently
 * {@code "prov:wasInformedBy"} (default / "normal"),
 * {@code "prov:wasRevisionOf"} ("re-test"),
 * {@code "fair2r:repairs"} ("rework"), and
 * {@code "fair2r:concession"} ("concession / use-as-is").
 *
 * <p>The endpoint is annotative — it does NOT create a new predecessor edge;
 * the edge must already exist. See {@code CreateDataObjectV2IO.typedPredecessors}
 * for the create-time shape.
 */
@Schema(
  name = "PredecessorEdgePatch",
  description =
    "QM1b — set the PROV-O / FAIR²R relationship type for an existing " +
    "predecessor edge. Allowed values: 'prov:wasInformedBy' (default / 'normal'), " +
    "'prov:wasRevisionOf' ('re-test'), 'fair2r:repairs' ('rework'), " +
    "'fair2r:concession' ('concession / use-as-is')."
)
public record PredecessorEdgePatchIO(
  @Schema(
    required = true,
    description =
      "PROV-O / FAIR²R relationship type. One of: " +
      "'prov:wasInformedBy', 'prov:wasRevisionOf', 'fair2r:repairs', 'fair2r:concession'.",
    enumeration = {
      "prov:wasInformedBy",
      "prov:wasRevisionOf",
      "fair2r:repairs",
      "fair2r:concession"
    },
    example = "fair2r:concession"
  )
  String relationshipType
) {}
