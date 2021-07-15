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

	private Long parentId;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long[] incomingIds;

	public DataObjectIO(DataObject dataObject) {
		super(dataObject);
		this.collectionId = dataObject.getCollection().getId();
		this.referenceIds = extractIds(dataObject.getReferences());
		this.successorIds = extractIds(dataObject.getSuccessors());
		this.predecessorIds = extractIds(dataObject.getPredecessors());
		this.childrenIds = extractIds(dataObject.getChildren());
		this.parentId = dataObject.getParent() != null ? dataObject.getParent().getId() : null;
		this.incomingIds = extractIds(dataObject.getIncoming());
	}

}
