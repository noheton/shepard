package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.FileContainer;

public class FileContainerDAO extends GenericDAO<FileContainer> {

	@Override
	public Class<FileContainer> getEntityType() {
		return FileContainer.class;
	}

}
