package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.BasicReference;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "BasicReference")
public class BasicReferenceIO extends AbstractEntityIO {

	private String name;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private long dataObjectId;

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String type;

	public BasicReferenceIO(BasicReference ref) {
		super(ref);
		this.type = ref.getType();
		this.name = ref.getName();
		this.dataObjectId = ref.getDataObject().getId();
	}
}
