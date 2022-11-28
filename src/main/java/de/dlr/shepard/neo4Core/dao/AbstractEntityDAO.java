package de.dlr.shepard.neo4Core.dao;

import java.util.Collections;
import java.util.Map;

import org.neo4j.ogm.session.Session;

import de.dlr.shepard.neo4Core.entities.AbstractEntity;
import de.dlr.shepard.neo4j.NeoConnector;

public class AbstractEntityDAO {

	private Session session = null;

	public AbstractEntityDAO() {
		session = NeoConnector.getInstance().getNeo4jSession();
	}

	public AbstractEntity find(long id) {
		var result = session.query(String.format("MATCH (n) WHERE ID(n) = %d RETURN n", id), Collections.emptyMap());
		if (!result.queryResults().iterator().hasNext()) {
			return null;
		}
		Map<String, Object> entityMap = result.queryResults().iterator().next();
		if (entityMap.get("n") instanceof AbstractEntity abstractEntity) {
			return abstractEntity;
		}
		return null;
	}

	public void update(AbstractEntity entity) {
		session.save(entity);
	}

}
