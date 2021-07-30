package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.CollectionReference;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "CollectionReference")
public class CollectionReferenceIO extends BasicReferenceIO {

	private long referencedCollectionId;

	private String relationship;

	public CollectionReferenceIO(CollectionReference ref) {
		super(ref);
		this.referencedCollectionId = ref.getReferencedCollection() != null ? ref.getReferencedCollection().getId()
				: -1;
		this.relationship = ref.getRelationship();
	}

}
