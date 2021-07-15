package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.DataObjectReference;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "DataObjectReference")
public class DataObjectReferenceIO extends BasicReferenceIO {

	private long referencedDataObjectId;

	private String relationship;

	public DataObjectReferenceIO(DataObjectReference ref) {
		super(ref);
		this.referencedDataObjectId = ref.getReferencedDataObject() != null ? ref.getReferencedDataObject().getId()
				: -1;
		this.relationship = ref.getRelationship();
	}

}
