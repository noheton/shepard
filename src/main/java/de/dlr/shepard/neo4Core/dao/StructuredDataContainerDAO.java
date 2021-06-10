package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;

public class StructuredDataContainerDAO extends GenericDAO<StructuredDataContainer> {

	@Override
	public Class<StructuredDataContainer> getEntityType() {
		return StructuredDataContainer.class;
	}

}
