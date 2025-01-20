package de.dlr.shepard.common.search.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.neo4j.entities.ContainerType;
import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.common.search.Neo4jEmitter;
import de.dlr.shepard.common.search.SearchDAO;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.data.file.daos.FileContainerDAO;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.structureddata.daos.StructuredDataContainerDAO;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class ContainerSearcherTest {

  @InjectMock
  TimeseriesContainerDAO timeseriesContainerDAO;

  @InjectMock
  StructuredDataContainerDAO structuredDataContainerDAO;

  @InjectMock
  FileContainerDAO fileContainerDAO;

  @InjectMock
  SearchDAO searchDAO;

  @Inject
  ContainerSearcher containerSearcher;

  @Test
  public void searchFileContainerTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    ContainerSearchParams params = new ContainerSearchParams(JSONquery, ContainerType.FILE);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    QueryParamHelper queryParamHelper = new QueryParamHelper();
    String username = "EngelsFriedrich";
    String neo4jFileSelectionQuery = Neo4jEmitter.emitContainerSelectionQuery(
      JSONquery,
      ContainerType.FILE,
      queryParamHelper,
      username
    );
    FileContainer fileRes = new FileContainer(5L);
    List<BasicContainer> fileResList = new ArrayList<>();
    fileResList.add(fileRes);
    when(
      searchDAO.findContainers(neo4jFileSelectionQuery, queryParamHelper, Constants.FILECONTAINER_IN_QUERY)
    ).thenReturn(fileResList);
    var actual = containerSearcher.search(searchBody, queryParamHelper, username);
    assertThat(actual.getResults()).containsExactly(new BasicContainerIO(fileRes));
    assertThat(actual.getSearchParams()).isEqualTo(params);
  }

  @Test
  public void searchTimeseriesContainerTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    ContainerSearchParams params = new ContainerSearchParams(JSONquery, ContainerType.TIMESERIES);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    QueryParamHelper queryParamHelper = new QueryParamHelper();
    String username = "EngelsFriedrich";
    String neo4jTimeseriesQuery = Neo4jEmitter.emitContainerSelectionQuery(
      JSONquery,
      ContainerType.TIMESERIES,
      queryParamHelper,
      username
    );
    TimeseriesContainer timeRes1 = new TimeseriesContainer(5L);
    TimeseriesContainer timeRes2 = new TimeseriesContainer(8L);
    List<BasicContainer> timeResList = List.of(timeRes1, timeRes2);
    when(
      searchDAO.findContainers(neo4jTimeseriesQuery, queryParamHelper, Constants.TIMESERIESCONTAINER_IN_QUERY)
    ).thenReturn(timeResList);
    var actual = containerSearcher.search(searchBody, queryParamHelper, username);
    assertThat(actual.getResults()).containsExactly(new BasicContainerIO(timeRes1), new BasicContainerIO(timeRes2));
  }

  @Test
  public void searchStructuredDataContainerTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\",\"operator\": \"eq\"}";
    ContainerSearchParams params = new ContainerSearchParams(JSONquery, ContainerType.STRUCTUREDDATA);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    QueryParamHelper queryParamHelper = new QueryParamHelper();
    String username = "EngelsFriedrich";
    String neo4jStructuredDataSelectionQuery = Neo4jEmitter.emitContainerSelectionQuery(
      JSONquery,
      ContainerType.STRUCTUREDDATA,
      queryParamHelper,
      username
    );
    StructuredDataContainer sdRes1 = new StructuredDataContainer(5L);
    StructuredDataContainer sdRes2 = new StructuredDataContainer(8L);
    List<BasicContainer> sdResList = List.of(sdRes1, sdRes2);
    when(
      searchDAO.findContainers(
        neo4jStructuredDataSelectionQuery,
        queryParamHelper,
        Constants.STRUCTUREDDATACONTAINER_IN_QUERY
      )
    ).thenReturn(sdResList);
    var actual = containerSearcher.search(searchBody, queryParamHelper, username);
    assertThat(actual.getResults()).containsExactly(new BasicContainerIO(sdRes1), new BasicContainerIO(sdRes2));
  }
}
