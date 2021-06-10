package de.dlr.shepard.neo4Core.io;

import java.util.List;

import de.dlr.shepard.mongoDB.StructuredData;
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

	private List<StructuredData> structuredDatas;

	private long structuredDataContainerId;

	public StructuredDataReferenceIO(StructuredDataReference ref) {
		super(ref);
		this.structuredDatas = ref.getStructuredDatas();
		this.structuredDataContainerId = ref.getStructuredDataContainer() != null
				? ref.getStructuredDataContainer().getId()
				: -1;
	}

}
