package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.util.Constants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FileContainer extends AbstractContainer {

	private String mongoId;

	@Relationship(type = Constants.FILE_IN_CONTAINER)
	private List<ShepardFile> files = new ArrayList<>();

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public FileContainer(long id) {
		super(id);
	}

	public void addFile(ShepardFile file) {
		files.add(file);
	}

}
