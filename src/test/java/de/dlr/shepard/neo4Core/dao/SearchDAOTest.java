package de.dlr.shepard.neo4Core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;

public class SearchDAOTest extends BaseTestCase {

	@Mock
	private Session session;

	@InjectMocks
	private SearchDAO dao;

	@Test
	public void buildQueryAndGetIdDictionaryFromQueryTest() {
		// prepare Iterator
		Map<String, Object> resultTuple = new HashMap<String, Object>();
		resultTuple.put("id(col)", 1L);
		HashSet<Map<String, Object>> resultSet = new HashSet<Map<String, Object>>();
		resultSet.add(resultTuple);
		Iterator<Map<String, Object>> resultIterator = resultSet.iterator();
		var result = mock(Result.class);
		when(result.iterator()).thenReturn(resultIterator);

		String selectionQuery = "MATCH bla ";
		String query = selectionQuery + " RETURN id(col)";
		String[] variables = { "col" };
		when(session.query(query, Collections.emptyMap())).thenReturn(result);
		List<Map<String, Long>> expected = dao.buildQueryAndGetIdDictionaryFromQuery(selectionQuery, variables);
		assertEquals(expected.size(), 1);
		assertEquals(expected.get(0).get("col"), 1L);
	}

	@Test
	public void findFileContainersTest() {
		FileContainer fileContainer = new FileContainer(1L);
		HashSet<FileContainer> fileContainerSet = new HashSet<FileContainer>();
		fileContainerSet.add(fileContainer);
		String selectionQuery = "MATCH bla ";
		String query = "MATCH bla  WITH fc MATCH path=(fc)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN fc, nodes(path), relationships(path)";
		when(session.query(FileContainer.class, query, Collections.emptyMap())).thenReturn(fileContainerSet);
		List<FileContainer> fileContainers = dao.findFileContainers(selectionQuery, "fc");
		assertEquals(fileContainers.size(), 1);
		assertEquals(fileContainers.get(0), fileContainer);
	}

	@Test
	public void findStructuredDataContainersTest() {
		StructuredDataContainer structuredDataContainer = new StructuredDataContainer(1L);
		HashSet<StructuredDataContainer> structuredDataContainerSet = new HashSet<StructuredDataContainer>();
		structuredDataContainerSet.add(structuredDataContainer);
		String selectionQuery = "MATCH bla ";
		String query = "MATCH bla  WITH sd MATCH path=(sd)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN sd, nodes(path), relationships(path)";
		when(session.query(StructuredDataContainer.class, query, Collections.emptyMap()))
				.thenReturn(structuredDataContainerSet);
		List<StructuredDataContainer> structuredDataContainers = dao.findStructuredDataContainers(selectionQuery, "sd");
		assertEquals(structuredDataContainers.size(), 1);
		assertEquals(structuredDataContainers.get(0), structuredDataContainer);
	}

	@Test
	public void findTimeseriesContainersTest() {
		TimeseriesContainer timeseriesContainer = new TimeseriesContainer(1L);
		HashSet<TimeseriesContainer> timeseriesContainerSet = new HashSet<TimeseriesContainer>();
		timeseriesContainerSet.add(timeseriesContainer);
		String selectionQuery = "MATCH bla ";
		String query = "MATCH bla  WITH ts MATCH path=(ts)-[*0..1]->(n) WHERE n.deleted = FALSE OR n.deleted IS NULL RETURN ts, nodes(path), relationships(path)";
		when(session.query(TimeseriesContainer.class, query, Collections.emptyMap()))
				.thenReturn(timeseriesContainerSet);
		List<TimeseriesContainer> timeseriesContainers = dao.findTimeseriesContainers(selectionQuery, "ts");
		assertEquals(timeseriesContainers.size(), 1);
		assertEquals(timeseriesContainers.get(0), timeseriesContainer);
	}

}
