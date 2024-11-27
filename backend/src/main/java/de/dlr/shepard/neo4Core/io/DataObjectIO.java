package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.DataObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
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

  public DataObjectIO(DataObject dataObject) {
    super(dataObject);
    this.collectionId = dataObject.getCollection().getShepardId();
    this.referenceIds = extractShepardIds(dataObject.getReferences());
    this.successorIds = extractShepardIds(dataObject.getSuccessors());
    this.predecessorIds = extractShepardIds(dataObject.getPredecessors());
    this.childrenIds = extractShepardIds(dataObject.getChildren());
    this.parentId = dataObject.getParent() != null ? dataObject.getParent().getShepardId() : null;
    this.incomingIds = extractShepardIds(dataObject.getIncoming());
  }
}
