package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "StructuredDataReference")
public class StructuredDataReferenceIO extends BasicReferenceIO {

	@NotEmpty
	private String[] structuredDataOids;

	@NotNull
	private long structuredDataContainerId;

	public StructuredDataReferenceIO(StructuredDataReference ref) {
		super(ref);
		this.structuredDataOids = ref.getStructuredDatas().stream().map(StructuredData::getOid).toArray(String[]::new);
		this.structuredDataContainerId = ref.getStructuredDataContainer() != null
				? ref.getStructuredDataContainer().getId()
				: -1;
	}

}
