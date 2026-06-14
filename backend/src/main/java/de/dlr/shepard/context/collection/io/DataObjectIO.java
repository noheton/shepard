package de.dlr.shepard.context.collection.io;

import de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.context.references.videostreamreference.VideoPayload;
import java.util.Arrays;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "DataObject")
public class DataObjectIO extends AbstractDataObjectIO {

  @Schema(readOnly = true, required = true)
  private long collectionId;

  @Schema(readOnly = true, required = true)
  private long[] referenceIds;

  @Schema(readOnly = true, required = true)
  private long[] successorIds;

  private long[] predecessorIds;

  /**
   * BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH — appId (UUID v7) companion for
   * {@link #predecessorIds}. On GET responses, populated in parallel with
   * {@code predecessorIds} (nulls filtered for pre-L2b rows that lack appIds).
   *
   * <p>On PATCH bodies: when non-null and non-empty, overrides {@code predecessorIds}
   * as the authoritative predecessor source. Callers on post-L2b instances (UUID v7
   * only, no numeric shepardId) MUST use this field instead of {@code predecessorIds}.
   *
   * <p>Back-compat: callers that only send {@code predecessorIds} are unaffected —
   * if this field is absent / null in the request, the service falls back to
   * {@code predecessorIds}. If both are present, this field wins.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    description =
      "BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH: appId (UUID v7) array of predecessor " +
      "DataObjects. On PATCH bodies, when non-null and non-empty, overrides predecessorIds. " +
      "On GET responses, populated alongside predecessorIds; nulls filtered for pre-L2b rows."
  )
  private String[] predecessorAppIds;

  @Schema(readOnly = true, required = true)
  private long[] childrenIds;

  @Schema(nullable = true, required = true)
  private Long parentId;

  @Schema(readOnly = true, required = true)
  private long[] incomingIds;

  /**
   * @deprecated Use {@code timeseriesCount} from {@link de.dlr.shepard.v2.dataobject.io.DataObjectListItemV2IO}
   * on the {@code /v2/} list endpoint instead. Two semantic differences: (1) this field
   * counts ALL references including soft-deleted ones; the v2 {@code timeseriesCount} counts
   * non-deleted references only. (2) this field is an {@code int} (overflow-unsafe for large
   * collections); the v2 field is a {@code long}. Consolidation deferred to L2e (breaking
   * API version bump).
   */
  @Deprecated
  @Schema(
    readOnly = true,
    required = true,
    deprecated = true,
    description = "Deprecated — use `timeseriesCount` on the /v2/ DataObjectListItemV2 shape instead. " +
      "This int includes soft-deleted references; the v2 long field counts non-deleted only."
  )
  private int timeseriesReferenceCount;

  /**
   * @deprecated Use {@code fileCount} from {@link de.dlr.shepard.v2.dataobject.io.DataObjectListItemV2IO}
   * on the {@code /v2/} list endpoint instead. Three semantic differences: (1) this field
   * counts only {@code :FileBundleReference} nodes (FR1a bundles); the v2 {@code fileCount}
   * counts both {@code :FileReference} (bundles) and {@code :SingletonFileReference} (FR1b
   * singletons), making it the correct total. (2) this field includes soft-deleted references;
   * v2 counts non-deleted only. (3) this field is an {@code int}; the v2 field is a {@code long}.
   * Consolidation deferred to L2e (breaking API version bump).
   */
  @Deprecated
  @Schema(
    readOnly = true,
    required = true,
    deprecated = true,
    description = "Deprecated — use `fileCount` on the /v2/ DataObjectListItemV2 shape instead. " +
      "This int counts only FileBundleReference nodes (FR1a); the v2 long also counts " +
      "SingletonFileReference (FR1b) and excludes soft-deleted references."
  )
  private int fileBundleCount;

  /**
   * @deprecated Use {@code structuredDataCount} from
   * {@link de.dlr.shepard.v2.dataobject.io.DataObjectListItemV2IO} on the {@code /v2/} list
   * endpoint instead. Two semantic differences: (1) this field counts ALL references including
   * soft-deleted ones; the v2 {@code structuredDataCount} counts non-deleted references only.
   * (2) this field is an {@code int} (overflow-unsafe for large collections); the v2 field is
   * a {@code long}. Consolidation deferred to L2e (breaking API version bump).
   */
  @Deprecated
  @Schema(
    readOnly = true,
    required = true,
    deprecated = true,
    description = "Deprecated — use `structuredDataCount` on the /v2/ DataObjectListItemV2 shape instead. " +
      "This int includes soft-deleted references; the v2 long field counts non-deleted only."
  )
  private int structuredDataReferenceCount;

  /**
   * TOOLS-CONTEXT-DO-TEMPLATE-DETECT-1 — {@code appId} of the
   * {@code :ShepardTemplate} this DataObject was created from, or
   * {@code null} when the DataObject was created without a template
   * (or predates the template wiring). Read-only — minted server-side
   * by {@code ShepardTemplateDAO.recordCreatedFrom} at create time.
   *
   * <p>Surfaced so the frontend can render the in-context tools menu's
   * <strong>Validate against shape</strong> / <strong>Render view</strong>
   * actions only when a template is attached (the destination tools
   * accept the {@code templateAppId} as a query param for prefill).
   *
   * <p>Absent from the wire when {@code null} thanks to {@link JsonInclude#NON_NULL},
   * so upstream v5.2.0 clients see no change in the wire shape on
   * DataObjects that lack an attached template.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
    nullable = true,
    readOnly = true,
    description = "TOOLS-CONTEXT-DO-TEMPLATE-DETECT-1: appId of the :ShepardTemplate this DataObject was created from. " +
      "Stamped server-side at create time; read-only. Absent from the wire when null."
  )
  private String attachedTemplateAppId;

  /**
   * No v2 {@code long} counterpart exists yet for video-stream references.
   * This field remains active (not deprecated) until {@code DataObjectListItemV2IO} grows a
   * {@code videoCount} field. Tracked in the backlog as API2-video. The {@code int} type will
   * be unsafe if a DataObject ever holds more than ~2 billion video references, but that
   * scenario is not practically expected. When a v2 field lands, deprecate this field then.
   */
  @Schema(readOnly = true, required = true)
  private int videoStreamReferenceCount;

  public DataObjectIO(DataObject dataObject) {
    super(dataObject);
    this.collectionId = dataObject.getCollection().getShepardId();
    this.referenceIds = extractShepardIds(dataObject.getReferences());
    this.successorIds = extractShepardIds(dataObject.getSuccessors());
    this.predecessorIds = extractShepardIds(dataObject.getPredecessors());
    this.predecessorAppIds = dataObject.getPredecessors().stream()
        .map(DataObject::getAppId)
        .filter(Objects::nonNull)
        .toArray(String[]::new);
    this.childrenIds = extractShepardIds(dataObject.getChildren());
    this.parentId = dataObject.getParent() != null ? dataObject.getParent().getShepardId() : null;
    this.incomingIds = extractShepardIds(dataObject.getIncoming());
    this.timeseriesReferenceCount = (int) dataObject.getReferences().stream()
        .filter(r -> r instanceof TimeseriesReference).count();
    this.fileBundleCount = (int) dataObject.getReferences().stream()
        .filter(r -> r instanceof FileBundleReference).count();
    this.structuredDataReferenceCount = (int) dataObject.getReferences().stream()
        .filter(r -> r instanceof StructuredDataReference).count();
    this.videoStreamReferenceCount = (int) dataObject.getReferences().stream()
        .filter(r -> r instanceof VideoPayload).count();
    this.attachedTemplateAppId = dataObject.getAttachedTemplateAppId();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (!(o instanceof DataObjectIO)) return false;
    DataObjectIO other = (DataObjectIO) o;
    return (
      collectionId == other.collectionId &&
      HasId.areEqualSets(referenceIds, other.referenceIds) &&
      HasId.areEqualSets(successorIds, other.successorIds) &&
      HasId.areEqualSets(predecessorIds, other.predecessorIds) &&
      Arrays.equals(predecessorAppIds, other.predecessorAppIds) &&
      HasId.areEqualSets(childrenIds, other.childrenIds) &&
      Objects.equals(parentId, other.parentId) &&
      HasId.areEqualSets(incomingIds, other.incomingIds) &&
      Objects.equals(attachedTemplateAppId, other.attachedTemplateAppId)
    );
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((Long) collectionId).hashCode();
    result = prime * result + HasId.hashcodeHelper(referenceIds);
    result = prime * result + HasId.hashcodeHelper(successorIds);
    result = prime * result + HasId.hashcodeHelper(childrenIds);
    result = prime * result + HasId.hashcodeHelper(incomingIds);
    result = prime * result + HasId.hashcodeHelper(predecessorIds);
    result = prime * result + Arrays.hashCode(predecessorAppIds);
    result = prime * result + Objects.hashCode(parentId);
    result = prime * result + Objects.hashCode(attachedTemplateAppId);

    return result;
  }
}
