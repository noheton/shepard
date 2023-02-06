package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.neo4j.NeoConnector;

public class SearchDAO {

	protected Session session = null;

	public SearchDAO() {
		session = NeoConnector.getInstance().getNeo4jSession();
	}

	public List<Long[]> getIdsFromQuery(String query, String[] variables) {
		List<Long[]> ret = new ArrayList<>();
		// TODO: sanity check for query
		Result idTuples = session.query(query, Collections.emptyMap());
		Iterator<Map<String, Object>> iterator = idTuples.iterator();
		Map<String, Object> map;
		while (iterator.hasNext()) {
			Long[] idTuple = new Long[variables.length];
			map = iterator.next();
			for (int i = 0; i < variables.length; i++) {
				idTuple[i] = (Long) map.get("id(" + variables[i] + ")");
			}
			ret.add(idTuple);
		}
		return ret;
	}

}
