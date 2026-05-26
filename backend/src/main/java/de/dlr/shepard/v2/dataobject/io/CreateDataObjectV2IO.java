package de.dlr.shepard.v2.dataobject.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * PROV1j — v2-only create request body for
 * {@code POST /v2/collections/{cid}/data-objects}.
 *
 * <p>Extends the upstream-frozen {@link DataObjectIO} (which MUST NOT be
 * modified per the API-version policy) with the optional {@code provenanceMode}
 * field that signals the EU AI Act Art. 50 per-artefact visibility requirement.
 *
 * <p>All fields from {@link DataObjectIO} ({@code name}, {@code description},
 * {@code attributes}, {@code status}, {@code license}, {@code accessRights},
 * {@code predecessorIds}, {@code parentId}) remain fully supported. The new
 * {@code provenanceMode} field is additive and optional: callers that do not
 * set it receive the server-side auto-detected mode (inferred from the
 * {@code X-AI-Agent} request header when present).
 *
 * <p>This class is <em>only</em> used as the deserialized request body type in
 * {@link de.dlr.shepard.v2.dataobject.resources.DataObjectV2Rest#create} and
 * as the input to the {@code instanceof CreateDataObjectV2IO} check in
 * {@link de.dlr.shepard.context.collection.services.DataObjectService#createDataObject}.
 * It is never sent back to a caller — responses use {@link DataObjectDetailV2IO}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(
  name = "CreateDataObjectV2",
  description =
    "v2 DataObject create request body. Extends DataObjectIO with the optional " +
    "`provenanceMode` field (EU AI Act Art. 50 per-artefact visibility). " +
    "When omitted, the server auto-detects from the X-AI-Agent request header."
)
public class CreateDataObjectV2IO extends DataObjectIO {

  /**
   * PROV1j — agent mode under which this DataObject is being created.
   *
   * <p>Allowed values: {@code "human"}, {@code "ai"}, {@code "collaborative"}.
   * When {@code null} (the default), the server auto-detects: if the
   * {@code X-AI-Agent} request header is present and non-blank, the mode is
   * set to {@code "ai"}; otherwise it is left as {@code null} (semantically
   * equivalent to human-authored for display purposes).
   *
   * <p>Omitted from JSON serialisation when {@code null} to keep the response
   * shape clean on the common (non-AI) path.
   */
  @Schema(
    description =
      "EU AI Act Art. 50 provenance mode: 'human', 'ai', or 'collaborative'. " +
      "When omitted, auto-detected from the X-AI-Agent request header. " +
      "Null in the response means human-authored (default).",
    nullable = true,
    enumeration = {"human", "ai", "collaborative"}
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String provenanceMode;

  /**
   * PROV1k — typed predecessor relationships.
   *
   * <p>Optional alternative to (or complement of) the inherited {@code predecessorIds}
   * long array. When present, each entry carries a PROV-O / FAIR²R relationship
   * type alongside the predecessor's {@code appId} (UUID v7). The service resolves
   * each {@code predecessorAppId} to a DataObject in the same collection and
   * populates the untyped {@code predecessors} relationship list for backward
   * compatibility.
   *
   * <p>Precedence rule: when {@code typedPredecessors} is non-null and non-empty,
   * it <em>overrides</em> the {@code predecessorIds} long array — the typed list
   * is the authoritative source for both the graph edges and the JSON property.
   * Existing callers that only send {@code predecessorIds} are unaffected (this
   * field is absent / null in their requests).
   *
   * <p>Validated by the service: each entry's {@code predecessorAppId} must be
   * non-blank and in the same collection; {@code relationshipType} must be one of
   * the allowed types or null (defaults to {@code "prov:wasInformedBy"}).
   */
  @Schema(
    nullable = true,
    description =
      "PROV1k — typed predecessor relationships. " +
      "When provided, overrides predecessorIds. " +
      "Each entry must name a predecessorAppId (UUID v7, same collection) and an optional " +
      "relationshipType ('prov:wasInformedBy' default, 'prov:wasRevisionOf', 'fair2r:repairs')."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<TypedPredecessorIO> typedPredecessors;


  // NOTE: embargoEndDate is inherited from AbstractDataObjectIO (via DataObjectIO).
  // It is user-provided and settable on create via the parent field.
  //
  // createdByOrcid is deliberately absent from this IO — it is server-stamped at
  // create time from User.orcid and is never accepted as user input (FAIR2).
}
