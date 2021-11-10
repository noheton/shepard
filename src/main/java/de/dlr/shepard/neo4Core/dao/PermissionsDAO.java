package de.dlr.shepard.neo4Core.dao;

import java.util.Collections;

import de.dlr.shepard.neo4Core.entities.Permissions;

public class PermissionsDAO extends GenericDAO<Permissions> {

	public Permissions findByEntity(long entityId) {
		String query = String.format("MATCH (e)-[:has_permissions]->(p:Permissions) WHERE ID(e) = %d ", entityId)
				+ getReturnPart("p");
		var permissions = findByQuery(query, Collections.emptyMap());
		if (permissions.iterator().hasNext())
			return permissions.iterator().next();
		return null;
	}

	public Permissions createWithEntity(Permissions permissions, long entityId) {
		var created = createOrUpdate(permissions);
		String query = String.format(
				"MATCH (e) WHERE ID(e) = %d MATCH (p:Permissions) WHERE ID(p) = %d CREATE path = (e)-[r:has_permissions]->(p)",
				entityId, created.getId());
		runQuery(query, Collections.emptyMap());
		return find(created.getId());
	}

	@Override
	public Class<Permissions> getEntityType() {
		return Permissions.class;
	}

}
