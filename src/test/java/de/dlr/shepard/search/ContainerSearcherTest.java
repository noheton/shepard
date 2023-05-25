package de.dlr.shepard.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataContainerDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;

public class ContainerSearcherTest extends BaseTestCase {

	@Mock
	private TimeseriesContainerDAO timeseriesContainerDAO;
	@Mock
	private StructuredDataContainerDAO structuredDataContainerDAO;
	@Mock
	private FileContainerDAO fileContainerDAO;

	@InjectMocks
	private ContainerSearcher containerSearcher;

	@Test
	public void searchFileContainerTest() {
		ContainerSearchBody searchBody = new ContainerSearchBody();
		ContainerSearchParams params = new ContainerSearchParams();
		ContainerQueryType type = ContainerQueryType.FILE;
		params.setQueryType(type);
		String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
		params.setQuery(JSONquery);
		searchBody.setSearchParams(params);
		String username = "EngelsFriedrich";
		String neo4jFileQuery = Neo4jEmitter.emitFileContainerQuery(JSONquery, username);
		FileContainer fileRes = new FileContainer();
		fileRes.setId(5L);
		List<FileContainer> fileResList = new ArrayList<>();
		fileResList.add(fileRes);
		when(fileContainerDAO.getFileContainerByQuery(neo4jFileQuery)).thenReturn(fileResList);
		assertThat(containerSearcher.search(searchBody, username).getFileContainers())
				.containsExactly(new FileContainerIO(fileRes));
		assertNull(containerSearcher.search(searchBody, username).getTimeseriesContainers());
		assertNull(containerSearcher.search(searchBody, username).getStructuredDataContainers());
	}

	@Test
	public void searchTimeseriesContainerTest() {
		ContainerSearchBody searchBody = new ContainerSearchBody();
		ContainerSearchParams params = new ContainerSearchParams();
		ContainerQueryType type = ContainerQueryType.TIMESERIES;
		params.setQueryType(type);
		String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
		params.setQuery(JSONquery);
		searchBody.setSearchParams(params);
		String username = "EngelsFriedrich";
		String neo4jTimeseriesQuery = Neo4jEmitter.emitTimeseriesContainerQuery(JSONquery, username);
		TimeseriesContainer timeRes1 = new TimeseriesContainer();
		timeRes1.setId(5L);
		TimeseriesContainer timeRes2 = new TimeseriesContainer();
		timeRes1.setId(8L);
		List<TimeseriesContainer> timeResList = new ArrayList<>();
		timeResList.add(timeRes1);
		timeResList.add(timeRes2);
		when(timeseriesContainerDAO.getTimeseriesContainerByQuery(neo4jTimeseriesQuery)).thenReturn(timeResList);
		assertEquals(2, containerSearcher.search(searchBody, username).getTimeseriesContainers().length);
		assertThat(containerSearcher.search(searchBody, username).getTimeseriesContainers())
				.contains(new TimeseriesContainerIO(timeRes1));
		assertThat(containerSearcher.search(searchBody, username).getTimeseriesContainers())
				.contains(new TimeseriesContainerIO(timeRes2));
		assertNull(containerSearcher.search(searchBody, username).getFileContainers());
		assertNull(containerSearcher.search(searchBody, username).getStructuredDataContainers());
	}

	@Test
	public void searchStructuredDataContainerTest() {
		ContainerSearchBody searchBody = new ContainerSearchBody();
		ContainerSearchParams params = new ContainerSearchParams();
		ContainerQueryType type = ContainerQueryType.STRUCTUREDDATA;
		params.setQueryType(type);
		String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
		params.setQuery(JSONquery);
		searchBody.setSearchParams(params);
		String username = "EngelsFriedrich";
		String neo4jStructuredDataQuery = Neo4jEmitter.emitStructuredDataContainerQuery(JSONquery, username);
		StructuredDataContainer sdRes1 = new StructuredDataContainer();
		sdRes1.setId(5L);
		StructuredDataContainer sdRes2 = new StructuredDataContainer();
		sdRes1.setId(8L);
		List<StructuredDataContainer> sdResList = new ArrayList<>();
		sdResList.add(sdRes1);
		sdResList.add(sdRes2);
		when(structuredDataContainerDAO.getStructuredDataContainerByQuery(neo4jStructuredDataQuery))
				.thenReturn(sdResList);
		assertEquals(2, containerSearcher.search(searchBody, username).getStructuredDataContainers().length);
		assertThat(containerSearcher.search(searchBody, username).getStructuredDataContainers())
				.contains(new StructuredDataContainerIO(sdRes1));
		assertThat(containerSearcher.search(searchBody, username).getStructuredDataContainers())
				.contains(new StructuredDataContainerIO(sdRes2));
		assertNull(containerSearcher.search(searchBody, username).getFileContainers());
		assertNull(containerSearcher.search(searchBody, username).getTimeseriesContainers());
	}

	@Test
	public void searchAllContainerTest() {
		ContainerSearchBody searchBody = new ContainerSearchBody();
		ContainerSearchParams params = new ContainerSearchParams();
		ContainerQueryType type = null;
		params.setQueryType(type);
		String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
		params.setQuery(JSONquery);
		searchBody.setSearchParams(params);
		String username = "EngelsFriedrich";
		String neo4jTimeseriesQuery = Neo4jEmitter.emitTimeseriesContainerQuery(JSONquery, username);
		String neo4jStructuredDataQuery = Neo4jEmitter.emitStructuredDataContainerQuery(JSONquery, username);
		String neo4jFileQuery = Neo4jEmitter.emitFileContainerQuery(JSONquery, username);
		StructuredDataContainer sdRes = new StructuredDataContainer();
		sdRes.setId(5L);
		List<StructuredDataContainer> sdResList = new ArrayList<>();
		sdResList.add(sdRes);
		TimeseriesContainer timeRes = new TimeseriesContainer();
		timeRes.setId(2L);
		List<TimeseriesContainer> timeResList = new ArrayList<>();
		timeResList.add(timeRes);
		FileContainer fileRes = new FileContainer();
		fileRes.setId(23L);
		List<FileContainer> fileResList = new ArrayList<>();
		fileResList.add(fileRes);
		when(structuredDataContainerDAO.getStructuredDataContainerByQuery(neo4jStructuredDataQuery))
				.thenReturn(sdResList);
		when(timeseriesContainerDAO.getTimeseriesContainerByQuery(neo4jTimeseriesQuery)).thenReturn(timeResList);
		when(fileContainerDAO.getFileContainerByQuery(neo4jFileQuery)).thenReturn(fileResList);
		ContainerSearchResult res = containerSearcher.search(searchBody, username);
		assertThat(res.getFileContainers()).containsExactly(new FileContainerIO(fileRes));
		assertThat(res.getTimeseriesContainers()).containsExactly(new TimeseriesContainerIO(timeRes));
		assertThat(res.getStructuredDataContainers()).containsExactly(new StructuredDataContainerIO(sdRes));
	}

}
