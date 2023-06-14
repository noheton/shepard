package de.dlr.shepard.neo4Core.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.CypherQueryHelper;

public class SearchDAO {

	protected Session session = null;

	public SearchDAO() {
		session = NeoConnector.getInstance().getNeo4jSession();
	}

	public List<Map<String, Long>> buildQueryAndGetIdDictionaryFromQuery(String selectionQuery, String[] variables) {
		List<Map<String, Long>> ret = new ArrayList<>();
		// TODO: sanity check for query
		String returnQuery = " RETURN ";
		var varList = Arrays.stream(variables).map(v -> "id(" + v + ")").toList();
		returnQuery = returnQuery + String.join(", ", varList);
		String query = selectionQuery + returnQuery;
		Result idTuples = session.query(query, Collections.emptyMap());
		Iterator<Map<String, Object>> iterator = idTuples.iterator();
		Map<String, Object> map;
		while (iterator.hasNext()) {
			Map<String, Long> idMap = new HashMap<>();
			map = iterator.next();
			for (int i = 0; i < variables.length; i++) {
				idMap.put(variables[i], (Long) map.get("id(" + variables[i] + ")"));
			}
			ret.add(idMap);
		}
		return ret;
	}

	public List<FileContainer> findFileContainers(String selectionQuery, String containerVariable) {
		String query = selectionQuery + emitContainerReturnPart(containerVariable);
		Iterable<FileContainer> fileContainers = session.query(FileContainer.class, query, Collections.emptyMap());
		ArrayList<FileContainer> ret = new ArrayList<FileContainer>();
		fileContainers.forEach(ret::add);
		return ret;
	}

	public List<StructuredDataContainer> findStructuredDataContainers(String selectionQuery, String containerVariable) {
		String query = selectionQuery + emitContainerReturnPart(containerVariable);
		Iterable<StructuredDataContainer> structuredDataContainers = session.query(StructuredDataContainer.class, query,
				Collections.emptyMap());
		ArrayList<StructuredDataContainer> ret = new ArrayList<StructuredDataContainer>();
		structuredDataContainers.forEach(ret::add);
		return ret;
	}

	public List<TimeseriesContainer> findTimeseriesContainers(String selectionQuery, String containerVariable) {
		String query = selectionQuery + emitContainerReturnPart(containerVariable);
		Iterable<TimeseriesContainer> timeseriesContainers = session.query(TimeseriesContainer.class, query,
				Collections.emptyMap());
		ArrayList<TimeseriesContainer> ret = new ArrayList<TimeseriesContainer>();
		timeseriesContainers.forEach(ret::add);
		return ret;
	}

	private String emitContainerReturnPart(String containerVariable) {
		return " WITH " + containerVariable + " " + CypherQueryHelper.getReturnPart(containerVariable, true);
	}

}
