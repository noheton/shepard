package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.DataObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "DataObject")
public class DataObjectIO extends AbstractDataObjectIO {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long collectionId;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long[] referenceIds;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long[] successorIds;

	private long[] predecessorIds;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long[] childrenIds;

	@Schema(nullable = true)
	private Long parentId;

	@Schema(accessMode = AccessMode.READ_ONLY)
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
