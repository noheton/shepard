package de.dlr.shepard.context.collection.io;

import de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
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

  @Schema(readOnly = true, required = true)
  private int timeseriesReferenceCount;

  @Schema(readOnly = true, required = true)
  private int fileBundleCount;

  @Schema(readOnly = true, required = true)
  private int structuredDataReferenceCount;

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
        .filter(r -> r instanceof VideoStreamReference).count();
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
