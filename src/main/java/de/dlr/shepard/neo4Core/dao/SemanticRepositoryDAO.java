package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.SemanticRepository;

public class SemanticRepositoryDAO extends GenericDAO<SemanticRepository> {

	@Override
	public Class<SemanticRepository> getEntityType() {
		return SemanticRepository.class;
	}

}
