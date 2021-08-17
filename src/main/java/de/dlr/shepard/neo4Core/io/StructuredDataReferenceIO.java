package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "StructuredDataReference")
public class StructuredDataReferenceIO extends BasicReferenceIO {

	private String[] structuredDataOids;

	private long structuredDataContainerId;

	public StructuredDataReferenceIO(StructuredDataReference ref) {
		super(ref);
		this.structuredDataOids = ref.getStructuredDatas().stream().map(sd -> sd.getOid()).toArray(String[]::new);
		this.structuredDataContainerId = ref.getStructuredDataContainer() != null
				? ref.getStructuredDataContainer().getId()
				: -1;
	}

}
