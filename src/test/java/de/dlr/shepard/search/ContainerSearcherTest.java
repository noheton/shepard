package de.dlr.shepard.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataContainerDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.util.Constants;

public class ContainerSearcherTest extends BaseTestCase {

	@Mock
	private TimeseriesContainerDAO timeseriesContainerDAO;
	@Mock
	private StructuredDataContainerDAO structuredDataContainerDAO;
	@Mock
	private FileContainerDAO fileContainerDAO;
	@Mock
	private SearchDAO searchDAO;

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
		String neo4jFileSelectionQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONquery, username);
		FileContainer fileRes = new FileContainer();
		fileRes.setId(5L);
		List<FileContainer> fileResList = new ArrayList<>();
		fileResList.add(fileRes);
		when(searchDAO.findFileContainers(neo4jFileSelectionQuery, Constants.FILECONTAINER_IN_QUERY))
				.thenReturn(fileResList);
		assertThat(containerSearcher.search(searchBody, username).getFileContainers())
				.containsExactly(new FileContainerIO(fileRes));
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
		String neo4jTimeseriesQuery = Neo4jEmitter.emitTimeseriesContainerSelectionQuery(JSONquery, username);
		TimeseriesContainer timeRes1 = new TimeseriesContainer();
		timeRes1.setId(5L);
		TimeseriesContainer timeRes2 = new TimeseriesContainer();
		timeRes1.setId(8L);
		List<TimeseriesContainer> timeResList = new ArrayList<>();
		timeResList.add(timeRes1);
		timeResList.add(timeRes2);
		when(searchDAO.findTimeseriesContainers(neo4jTimeseriesQuery, Constants.TIMESERIESCONTAINER_IN_QUERY))
				.thenReturn(timeResList);
		assertEquals(containerSearcher.search(searchBody, username).getTimeseriesContainers().length, 2);
		assertThat(containerSearcher.search(searchBody, username).getTimeseriesContainers())
				.contains(new TimeseriesContainerIO(timeRes1));
		assertThat(containerSearcher.search(searchBody, username).getTimeseriesContainers())
				.contains(new TimeseriesContainerIO(timeRes2));
	}

	@Test
	public void searchStructuredDataContainerTest() {
		ContainerSearchBody searchBody = new ContainerSearchBody();
		ContainerSearchParams params = new ContainerSearchParams();
		ContainerQueryType type = ContainerQueryType.STRUCTUREDDATA;
		params.setQueryType(type);
		String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\",\"operator\": \"eq\"}";
		params.setQuery(JSONquery);
		searchBody.setSearchParams(params);
		String username = "EngelsFriedrich";
		String neo4jStructuredDataSelectionQuery = Neo4jEmitter.emitStructuredDataContainerSelectionQuery(JSONquery,
				username);
		StructuredDataContainer sdRes1 = new StructuredDataContainer();
		sdRes1.setId(5L);
		StructuredDataContainer sdRes2 = new StructuredDataContainer();
		sdRes1.setId(8L);
		List<StructuredDataContainer> sdResList = new ArrayList<>();
		sdResList.add(sdRes1);
		sdResList.add(sdRes2);
		when(searchDAO.findStructuredDataContainers(neo4jStructuredDataSelectionQuery,
				Constants.STRUCTUREDDATACONTAINER_IN_QUERY)).thenReturn(sdResList);
		assertEquals(containerSearcher.search(searchBody, username).getStructuredDataContainers().length, 2);
		assertThat(containerSearcher.search(searchBody, username).getStructuredDataContainers())
				.contains(new StructuredDataContainerIO(sdRes1));
		assertThat(containerSearcher.search(searchBody, username).getStructuredDataContainers())
				.contains(new StructuredDataContainerIO(sdRes2));
	}

	@Test
	public void searchAllContainerTest() {
		ContainerSearchBody searchBody = new ContainerSearchBody();
		ContainerSearchParams params = new ContainerSearchParams();
		ContainerQueryType type = null;
		params.setQueryType(type);
		String JSONquery = "{\"property\": \"name\",\"value\": \"MyName\", \"operator\": \"eq\"}";
		params.setQuery(JSONquery);
		searchBody.setSearchParams(params);
		String username = "EngelsFriedrich";
		String neo4jTimeseriesSelectionQuery = Neo4jEmitter.emitTimeseriesContainerSelectionQuery(JSONquery, username);
		String neo4jStructuredDataSelectionQuery = Neo4jEmitter.emitStructuredDataContainerSelectionQuery(JSONquery,
				username);
		String neo4jFileSelectionQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONquery, username);
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
		when(searchDAO.findStructuredDataContainers(neo4jStructuredDataSelectionQuery,
				Constants.STRUCTUREDDATACONTAINER_IN_QUERY)).thenReturn(sdResList);
		when(searchDAO.findTimeseriesContainers(neo4jTimeseriesSelectionQuery, Constants.TIMESERIESCONTAINER_IN_QUERY))
				.thenReturn(timeResList);
		when(searchDAO.findFileContainers(neo4jFileSelectionQuery, Constants.FILECONTAINER_IN_QUERY))
				.thenReturn(fileResList);
		assertThat(containerSearcher.search(searchBody, username).getStructuredDataContainers())
				.contains(new StructuredDataContainerIO(sdRes));
		assertThat(containerSearcher.search(searchBody, username).getFileContainers())
				.contains(new FileContainerIO(fileRes));
		assertThat(containerSearcher.search(searchBody, username).getTimeseriesContainers())
				.contains(new TimeseriesContainerIO(timeRes));
	}

}
