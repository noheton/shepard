package de.dlr.shepard.search.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.FileContainerDAO;
import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataContainerDAO;
import de.dlr.shepard.neo4Core.dao.TimeseriesContainerDAO;
import de.dlr.shepard.neo4Core.entities.FileContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.TimeseriesContainer;
import de.dlr.shepard.neo4Core.io.BasicContainerIO;
import de.dlr.shepard.search.Neo4jEmitter;
import de.dlr.shepard.util.Constants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

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
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    ContainerSearchParams params = new ContainerSearchParams(JSONquery, ContainerQueryType.FILE);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    String username = "EngelsFriedrich";
    String neo4jFileSelectionQuery = Neo4jEmitter.emitFileContainerSelectionQuery(JSONquery, username);
    FileContainer fileRes = new FileContainer(5L);
    List<FileContainer> fileResList = new ArrayList<>();
    fileResList.add(fileRes);
    when(searchDAO.findFileContainers(neo4jFileSelectionQuery, Constants.FILECONTAINER_IN_QUERY)).thenReturn(
      fileResList
    );
    var actual = containerSearcher.search(searchBody, username);
    assertThat(actual.getResults()).containsExactly(new BasicContainerIO(fileRes));
    assertThat(actual.getSearchParams()).isEqualTo(params);
  }

  @Test
  public void searchTimeseriesContainerTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    ContainerSearchParams params = new ContainerSearchParams(JSONquery, ContainerQueryType.TIMESERIES);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    String username = "EngelsFriedrich";
    String neo4jTimeseriesQuery = Neo4jEmitter.emitTimeseriesContainerSelectionQuery(JSONquery, username);
    TimeseriesContainer timeRes1 = new TimeseriesContainer(5L);
    TimeseriesContainer timeRes2 = new TimeseriesContainer(8L);
    List<TimeseriesContainer> timeResList = List.of(timeRes1, timeRes2);
    when(searchDAO.findTimeseriesContainers(neo4jTimeseriesQuery, Constants.TIMESERIESCONTAINER_IN_QUERY)).thenReturn(
      timeResList
    );
    var actual = containerSearcher.search(searchBody, username);
    assertThat(actual.getResults()).containsExactly(new BasicContainerIO(timeRes1), new BasicContainerIO(timeRes2));
  }

  @Test
  public void searchStructuredDataContainerTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\",\"operator\": \"eq\"}";
    ContainerSearchParams params = new ContainerSearchParams(JSONquery, ContainerQueryType.STRUCTUREDDATA);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    String username = "EngelsFriedrich";
    String neo4jStructuredDataSelectionQuery = Neo4jEmitter.emitStructuredDataContainerSelectionQuery(
      JSONquery,
      username
    );
    StructuredDataContainer sdRes1 = new StructuredDataContainer(5L);
    StructuredDataContainer sdRes2 = new StructuredDataContainer(8L);
    List<StructuredDataContainer> sdResList = List.of(sdRes1, sdRes2);
    when(
      searchDAO.findStructuredDataContainers(
        neo4jStructuredDataSelectionQuery,
        Constants.STRUCTUREDDATACONTAINER_IN_QUERY
      )
    ).thenReturn(sdResList);
    var actual = containerSearcher.search(searchBody, username);
    assertThat(actual.getResults()).containsExactly(new BasicContainerIO(sdRes1), new BasicContainerIO(sdRes2));
  }
}
