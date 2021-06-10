package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.FileContainer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "FileContainer")
public class FileContainerIO extends AbstractContainerIO {

	@Schema(accessMode = AccessMode.READ_ONLY)
	private String oid;

	public FileContainerIO(FileContainer container) {
		super(container);
		this.oid = container.getMongoId();
	}

}
