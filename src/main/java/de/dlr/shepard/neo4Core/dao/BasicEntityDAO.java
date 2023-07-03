package de.dlr.shepard.neo4Core.dao;

import java.util.Collections;
import java.util.Map;

import org.neo4j.ogm.session.Session;

import de.dlr.shepard.neo4Core.entities.BasicEntity;
import de.dlr.shepard.neo4j.NeoConnector;

public class BasicEntityDAO {

	private Session session = null;

	public BasicEntityDAO() {
		session = NeoConnector.getInstance().getNeo4jSession();
	}

	public BasicEntity find(long id) {
		var result = session.query(String.format("MATCH (n) WHERE ID(n) = %d RETURN n", id), Collections.emptyMap());
		if (!result.queryResults().iterator().hasNext()) {
			return null;
		}
		Map<String, Object> entityMap = result.queryResults().iterator().next();
		if (entityMap.get("n") instanceof BasicEntity abstractEntity) {
			return abstractEntity;
		}
		return null;
	}

	public void update(BasicEntity entity) {
		session.save(entity);
	}

}
