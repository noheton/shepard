package de.dlr.shepard.neo4Core.entities;

import java.util.ArrayList;
import java.util.List;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.typeconversion.Convert;

import de.dlr.shepard.mongoDB.File;
import de.dlr.shepard.neo4Core.converter.FileConverter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FileContainer extends AbstractContainer {

	private String mongoId;

	@Property("filesJson")
	@Convert(FileConverter.class)
	private List<File> files = new ArrayList<>();

	/**
	 * For testing purposes only
	 *
	 * @param id identifies the entity
	 */
	public FileContainer(long id) {
		super(id);
	}

	public void addFile(File file) {
		files.add(file);
	}

}
