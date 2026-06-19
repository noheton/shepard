package de.dlr.shepard.v2.dataobject.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH — v2-only PATCH request body for
 * {@code PATCH /v2/collections/{cid}/data-objects/{did}}.
 *
 * <p>Extends the upstream-frozen {@link DataObjectIO} with the optional
 * {@code predecessorAppIds} field, letting callers address predecessor
 * DataObjects by stable {@code appId} (UUID v7) rather than the transient
 * Neo4j numeric id ({@code predecessorIds: long[]}).
 *
 * <p>Precedence when multiple predecessor fields appear in the patch body:
 * <ol>
 *   <li>{@code predecessorAppIds} — this field (highest priority)</li>
 *   <li>{@code predecessorIds} — inherited numeric fallback (backward compat)</li>
 * </ol>
 *
 * <p>To set relationship types on the created edges, call
 * {@code PATCH /v2/collections/{cid}/data-objects/{did}/predecessors/{predAppId}}
 * (QM1b) after linking.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(
  name = "PatchDataObjectV2",
  description =
    "v2 DataObject PATCH request body (RFC 7396 merge-patch). " +
    "Extends DataObjectIO with `predecessorAppIds` for appId-based predecessor addressing. " +
    "When `predecessorAppIds` is non-null it overrides the inherited `predecessorIds` long[]."
)
public class PatchDataObjectV2IO extends DataObjectIO {

  /**
   * BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH — stable appId-based predecessor list.
   *
   * <p>When non-null, replaces the inherited {@code predecessorIds: long[]} as
   * the authoritative predecessor source. Each value must be a UUID v7 appId of a
   * DataObject in the same Collection. An empty array ({@code []}) explicitly clears
   * all predecessors. A {@code null} value (field absent from the patch) defers to
   * the inherited {@code predecessorIds} field.
   *
   * <p>All created edges default to {@code "prov:wasInformedBy"}. Use
   * {@code PATCH .../predecessors/{predAppId}} (QM1b) to annotate edge types after linking.
   */
  @Schema(
    nullable = true,
    description =
      "BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH — appIds (UUID v7) of predecessor DataObjects. " +
      "When non-null, overrides the inherited `predecessorIds` (numeric) field. " +
      "Empty array clears all predecessors. Absent means fall through to `predecessorIds`. " +
      "All edges default to 'prov:wasInformedBy'; use PATCH .../predecessors/{predAppId} to set edge types."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String[] predecessorAppIds;

  public PatchDataObjectV2IO(DataObject dataObject) {
    super(dataObject);
    // predecessorAppIds is intentionally not populated from the existing entity —
    // it is only set when a caller explicitly includes it in the merge-patch body.
  }
}
