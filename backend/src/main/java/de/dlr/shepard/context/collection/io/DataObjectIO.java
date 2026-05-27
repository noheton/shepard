package de.dlr.shepard.context.collection.io;

import de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.videostreamreference.VideoPayload;
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
      HasId.areEqualSets(childrenIds, other.childrenIds) &&
      Objects.equals(parentId, other.parentId) &&
      HasId.areEqualSets(incomingIds, other.incomingIds)
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
    result = prime * result + Objects.hashCode(parentId);

    return result;
  }
}
