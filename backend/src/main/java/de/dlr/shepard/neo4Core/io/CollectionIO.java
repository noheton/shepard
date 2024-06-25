package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.Collection;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "Collection")
public class CollectionIO extends AbstractDataObjectIO {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long[] dataObjectIds;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long[] incomingIds;

	public CollectionIO(Collection collection) {
		super(collection);
		this.dataObjectIds = extractShepardIds(collection.getDataObjects());
		this.incomingIds = extractShepardIds(collection.getIncoming());
	}

}