package de.dlr.shepard.common.search.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.neo4j.entities.ContainerType;
import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.ContainerSearchBody;
import de.dlr.shepard.common.search.io.ContainerSearchParams;
import de.dlr.shepard.common.search.query.Neo4jQuery;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.SortingHelper;
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
public class ContainerSearchServiceTest {

  @InjectMock
  TimeseriesContainerDAO timeseriesContainerDAO;

  @InjectMock
  StructuredDataContainerDAO structuredDataContainerDAO;

  @InjectMock
  FileContainerDAO fileContainerDAO;

  @InjectMock
  SearchDAO searchDAO;

  @Inject
  ContainerSearchService containerSearcher;

  @InjectMock
  UserService userService;

  private final User user = new User("Testuser");

  @Test
  public void searchBasicContainerTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    ContainerSearchParams params = new ContainerSearchParams(JSONquery, ContainerType.BASIC);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    Neo4jQuery neo4jFileSelectionQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONquery,
      ContainerType.BASIC,
      new SortingHelper(null, null),
      user.getUsername()
    );
    BasicContainer contRes = new BasicContainer(5L);
    List<BasicContainer> contResList = new ArrayList<>();
    contResList.add(contRes);

    when(searchDAO.findContainers(neo4jFileSelectionQuery, null, Constants.BASICCONTAINER_IN_QUERY)).thenReturn(
      contResList
    );
    when(userService.getCurrentUser()).thenReturn(user);

    var actual = containerSearcher.search(searchBody, null, new SortingHelper(null, null));
    assertThat(actual.getResults()).containsExactly(new BasicContainerIO(contRes));
    assertThat(actual.getSearchParams()).isEqualTo(params);
  }

  @Test
  public void searchFileContainerTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    ContainerSearchParams params = new ContainerSearchParams(JSONquery, ContainerType.FILE);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    Neo4jQuery neo4jFileSelectionQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONquery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      user.getUsername()
    );
    FileContainer fileRes = new FileContainer(5L);
    List<BasicContainer> fileResList = new ArrayList<>();
    fileResList.add(fileRes);

    when(searchDAO.findContainers(neo4jFileSelectionQuery, null, Constants.FILECONTAINER_IN_QUERY)).thenReturn(
      fileResList
    );
    when(userService.getCurrentUser()).thenReturn(user);

    var actual = containerSearcher.search(searchBody, null, new SortingHelper(null, null));
    assertThat(actual.getResults()).containsExactly(new BasicContainerIO(fileRes));
    assertThat(actual.getSearchParams()).isEqualTo(params);
  }

  @Test
  public void searchTimeseriesContainerTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\", \"operator\": \"eq\"}";
    ContainerSearchParams params = new ContainerSearchParams(JSONquery, ContainerType.TIMESERIES);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    Neo4jQuery neo4jTimeseriesQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONquery,
      ContainerType.TIMESERIES,
      new SortingHelper(null, null),
      user.getUsername()
    );
    TimeseriesContainer timeRes1 = new TimeseriesContainer(5L);
    TimeseriesContainer timeRes2 = new TimeseriesContainer(8L);
    List<BasicContainer> timeResList = List.of(timeRes1, timeRes2);
    when(searchDAO.findContainers(neo4jTimeseriesQuery, null, Constants.TIMESERIESCONTAINER_IN_QUERY)).thenReturn(
      timeResList
    );
    when(userService.getCurrentUser()).thenReturn(user);

    var actual = containerSearcher.search(searchBody, null, new SortingHelper(null, null));
    assertThat(actual.getResults()).containsExactly(new BasicContainerIO(timeRes1), new BasicContainerIO(timeRes2));
  }

  @Test
  public void searchStructuredDataContainerTest() {
    String JSONquery = "{\"property\": \"name\", \"value\": \"MyName\",\"operator\": \"eq\"}";
    ContainerSearchParams params = new ContainerSearchParams(JSONquery, ContainerType.STRUCTUREDDATA);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);
    Neo4jQuery neo4jStructuredDataSelectionQuery = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONquery,
      ContainerType.STRUCTUREDDATA,
      new SortingHelper(null, null),
      user.getUsername()
    );
    StructuredDataContainer sdRes1 = new StructuredDataContainer(5L);
    StructuredDataContainer sdRes2 = new StructuredDataContainer(8L);
    List<BasicContainer> sdResList = List.of(sdRes1, sdRes2);
    when(
      searchDAO.findContainers(neo4jStructuredDataSelectionQuery, null, Constants.STRUCTUREDDATACONTAINER_IN_QUERY)
    ).thenReturn(sdResList);
    when(userService.getCurrentUser()).thenReturn(user);

    var actual = containerSearcher.search(searchBody, null, new SortingHelper(null, null));
    assertThat(actual.getResults()).containsExactly(new BasicContainerIO(sdRes1), new BasicContainerIO(sdRes2));
  }

  // ── UI21-BACKEND-Q: createdBy filter tests ─────────────────────────────────

  @Test
  public void searchContainerWithCreatedByFilter_happyPath() {
    // When createdBy is set, the service must build a query that includes the
    // createdBy predicate so only matching containers are returned.
    String JSONquery = "{\"property\": \"name\", \"value\": \"\", \"operator\": \"contains\"}";
    String ownerFilter = "alice";
    ContainerSearchParams params = new ContainerSearchParams(JSONquery, ContainerType.FILE, ownerFilter);
    ContainerSearchBody searchBody = new ContainerSearchBody(params);

    Neo4jQuery queryWithCreatedBy = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONquery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      user.getUsername(),
      ownerFilter
    );
    // Verify the generated Cypher contains the createdBy predicate
    assertThat(queryWithCreatedBy.cypher()).contains("createdBy");

    FileContainer fileRes = new FileContainer(7L);
    List<BasicContainer> resList = List.of(fileRes);
    when(searchDAO.findContainers(queryWithCreatedBy, null, Constants.FILECONTAINER_IN_QUERY)).thenReturn(resList);
    when(userService.getCurrentUser()).thenReturn(user);

    var actual = containerSearcher.search(searchBody, null, new SortingHelper(null, null));
    assertThat(actual.getResults()).containsExactly(new BasicContainerIO(fileRes));
    assertThat(actual.getSearchParams().getCreatedBy()).isEqualTo(ownerFilter);
  }

  @Test
  public void searchContainerWithCreatedByFilter_nullFilter_predicateAbsent() {
    // When createdBy is null, the generated Cypher must NOT include the createdBy predicate.
    String JSONquery = "{\"property\": \"name\", \"value\": \"\", \"operator\": \"contains\"}";
    Neo4jQuery queryWithoutCreatedBy = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONquery,
      ContainerType.BASIC,
      new SortingHelper(null, null),
      user.getUsername(),
      null
    );
    // The null createdBy must not inject the predicate
    assertThat(queryWithoutCreatedBy.cypher()).doesNotContain("toLower(c.createdBy)");
  }

  @Test
  public void searchContainerWithCreatedByFilter_blankFilter_predicateAbsent() {
    // When createdBy is blank (whitespace only), the predicate must also be absent.
    String JSONquery = "{\"property\": \"name\", \"value\": \"\", \"operator\": \"contains\"}";
    Neo4jQuery queryWithBlank = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONquery,
      ContainerType.TIMESERIES,
      new SortingHelper(null, null),
      user.getUsername(),
      "   "
    );
    assertThat(queryWithBlank.cypher()).doesNotContain("toLower(tc.createdBy)");
  }

  @Test
  public void searchContainerWithCreatedByFilter_queryParamIsBound() {
    // The createdBy value must be placed in the parameter map (not inlined in Cypher)
    // so the query is safe from injection.
    String JSONquery = "{\"property\": \"name\", \"value\": \"\", \"operator\": \"contains\"}";
    String ownerFilter = "bob";
    Neo4jQuery query = Neo4jQueryBuilder.containerSelectionQueryWithNeo4jId(
      JSONquery,
      ContainerType.FILE,
      new SortingHelper(null, null),
      user.getUsername(),
      ownerFilter
    );
    // The Cypher should not contain the literal owner string — it must be a parameter ref ($pN)
    assertThat(query.cypher()).doesNotContain(ownerFilter);
    // The parameter map must contain the lowercased value
    assertThat(query.params()).containsValue(ownerFilter.toLowerCase());
  }
}
