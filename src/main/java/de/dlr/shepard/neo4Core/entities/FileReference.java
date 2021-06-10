package de.dlr.shepard.neo4Core.entities;

import java.util.List;
import java.util.Objects;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import de.dlr.shepard.util.Constants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NodeEntity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class FileReference extends BasicReference {

	private List<String> fileoids;

	@ToString.Exclude
	@Relationship(type = Constants.IS_IN_CONTAINER)
	private FileContainer filecontainer;

	/**
	 * For testing purposes only
	 * 
	 * @param id identifies the entity
	 */
	public FileReference(long id) {
		super(id);
	}

	public void addFile(String fileoid) {
		this.fileoids.add(fileoid);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(fileoids);
		result = prime * result + HasId.hashcodeHelper(filecontainer);
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
		return HasId.equalsHelper(filecontainer, other.filecontainer) && Objects.equals(fileoids, other.fileoids);
	}

}
