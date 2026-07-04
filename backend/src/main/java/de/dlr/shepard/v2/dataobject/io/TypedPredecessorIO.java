package de.dlr.shepard.v2.dataobject.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PROV1k — typed predecessor relationship descriptor.
 *
 * <p>Used in {@link CreateDataObjectV2IO#getTypedPredecessors()} and surfaced
 * read-only in {@link DataObjectDetailV2IO#getTypedPredecessorSummaries()}.
 *
 * <p>The {@code relationshipType} distinguishes:
 * <ul>
 *   <li>{@code "prov:wasInformedBy"} — generic informational dependency
 *       (default; backward-compatible with the untyped predecessor model;
 *       equivalent to "normal" in the QM1b transition-kind vocabulary)</li>
 *   <li>{@code "prov:wasRevisionOf"} — a direct revision or correction of the
 *       predecessor (e.g. TR-006 corrects TR-004 after repair;
 *       equivalent to "re-test" in the QM1b transition-kind vocabulary)</li>
 *   <li>{@code "fair2r:repairs"} — rework / NCR-repair relationship (e.g.
 *       TR-005 is the repair action for TR-004's anomaly;
 *       equivalent to "rework" in the QM1b transition-kind vocabulary)</li>
 *   <li>{@code "fair2r:concession"} — concession / "use-as-is" disposition
 *       (QM1b; the successor was accepted under a concession after the
 *       predecessor failed its acceptance criterion)</li>
 * </ul>
 *
 * <p>On create the {@code predecessorAppId} is resolved to a DataObject within
 * the same collection. The resolved DataObject is added to
 * {@link de.dlr.shepard.context.collection.entities.DataObject#getPredecessors()}
 * and its shepardId is included in {@code predecessorIds[]} for compat.
 */
@Schema(
  name = "TypedPredecessor",
  description =
    "PROV1k — typed predecessor relationship. " +
    "predecessorAppId: UUID v7 appId of the predecessor DataObject (same collection). " +
    "relationshipType: PROV-O / FAIR²R predicate — " +
    "'prov:wasInformedBy' (default), 'prov:wasRevisionOf', 'fair2r:repairs', " +
    "or 'fair2r:concession' (QM1b concession / use-as-is disposition)."
)
public record TypedPredecessorIO(

  @Schema(
    required = true,
    description = "UUID v7 appId of the predecessor DataObject (must be in the same collection).",
    example = "01930a2b-fe4c-7e3c-9f1d-8a5b2c3d4e5f"
  )
  String predecessorAppId,

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    description =
      "PROV-O or FAIR²R relationship type. " +
      "Null or absent defaults to 'prov:wasInformedBy'. " +
      "Allowed values: 'prov:wasInformedBy', 'prov:wasRevisionOf', " +
      "'fair2r:repairs', 'fair2r:concession'.",
    enumeration = {
      "prov:wasInformedBy",
      "prov:wasRevisionOf",
      "fair2r:repairs",
      "fair2r:concession"
    },
    defaultValue = "prov:wasInformedBy"
  )
  String relationshipType

) {

  /** Canonical set of allowed relationship types. */
  public static final Set<String> ALLOWED_TYPES = Set.of(
    "prov:wasInformedBy",
    "prov:wasRevisionOf",
    "fair2r:repairs",
    // QM1b — concession / "use-as-is" disposition. EN 9100 §8.7.
    "fair2r:concession"
  );

  /** Default relationship type — generic PROV-O dependency (backward-compat). */
  public static final String DEFAULT_TYPE = "prov:wasInformedBy";

  /**
   * Returns the effective relationship type, defaulting to
   * {@link #DEFAULT_TYPE} when {@code relationshipType} is null.
   */
  public String effectiveRelationshipType() {
    return (relationshipType == null || relationshipType.isBlank())
      ? DEFAULT_TYPE
      : relationshipType;
  }

  /**
   * Validates that the {@code predecessorAppId} is non-blank and the
   * {@code relationshipType} (if provided) is in {@link #ALLOWED_TYPES}.
   *
   * @throws InvalidBodyException when validation fails
   */
  public void validate() {
    if (predecessorAppId == null || predecessorAppId.isBlank()) {
      throw new InvalidBodyException("TypedPredecessor: predecessorAppId must not be blank.");
    }
    if (relationshipType != null && !relationshipType.isBlank() && !ALLOWED_TYPES.contains(relationshipType)) {
      throw new InvalidBodyException(
        "TypedPredecessor: unknown relationshipType '%s'. Allowed: %s."
          .formatted(relationshipType, ALLOWED_TYPES)
      );
    }
  }
}
