package de.dlr.shepard.neo4Core.dao;

import java.util.UUID;

import de.dlr.shepard.neo4Core.entities.ApiKey;

public class ApiKeyDAO extends GenericDAO<ApiKey> {

	/**
	 * Find an apiKey by uid
	 * 
	 * @param id Identifies the apiKey
	 * @return the found apiKey
	 */
	public ApiKey find(UUID id) {
		ApiKey entity = session.load(getEntityType(), id, DEPTH_ENTITY);
		return entity;
	}

	/**
	 * Find an apiKey by uid
	 * 
	 * @param id Identifies the apiKey
	 * @return true if deletion was successful
	 */
	public boolean delete(UUID id) {
		ApiKey entity = session.load(getEntityType(), id);
		if (entity != null) {
			session.delete(entity);
			return true;
		}
		return false;
	}

	@Override
	public Class<ApiKey> getEntityType() {
		return ApiKey.class;
	}
}
