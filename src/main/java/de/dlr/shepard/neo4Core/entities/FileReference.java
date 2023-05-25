package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.HasId;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NodeEntity
@Data
@NoArgsConstructor
public class FileReference extends BasicReference {

	@Relationship(type = Constants.HAS_PAYLOAD)
	private List<ShepardFile> files = new ArrayList<>();

	@ToString.Exclude
	@Relationship(type = Constants.IS_IN_CONTAINER)
	private FileContainer fileContainer;

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public FileReference(long id) {
		super(id);
	}

	public void addFile(ShepardFile file) {
		files.add(file);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(files);
		result = prime * result + HasId.hashcodeHelper(fileContainer);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof FileReference))
			return false;
		FileReference other = (FileReference) obj;
		return HasId.equalsHelper(fileContainer, other.fileContainer) && Objects.equals(files, other.files);
	}

}
