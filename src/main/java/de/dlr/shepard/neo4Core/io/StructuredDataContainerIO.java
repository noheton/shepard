package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "StructuredDataContainer")
public class StructuredDataContainerIO extends AbstractContainerIO {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String mongoid;

	public StructuredDataContainerIO(StructuredDataContainer container) {
		super(container);
		this.mongoid = container.getMongoId();
	}
}
