package de.dlr.shepard.neo4Core.io;

import java.util.List;

import de.dlr.shepard.mongoDB.File;
import de.dlr.shepard.neo4Core.entities.FileReference;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "FileReference")
public class FileReferenceIO extends BasicReferenceIO {

	private List<File> files;

	private long fileContainerId;

	public FileReferenceIO(FileReference ref) {
		super(ref);
		this.files = ref.getFiles();
		this.fileContainerId = ref.getFileContainer() != null ? ref.getFileContainer().getId() : -1;
	}

}
