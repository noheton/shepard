package de.dlr.shepard.search.unified;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.search.Neo4jEmitter;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.TraversalRules;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DataObjectSearcherTest extends BaseTestCase {

  @Mock
  private SearchDAO searchDAO;

  @InjectMocks
  private DataObjectSearcher dataObjectSearcher;

  private static String query = String.format(
    """
    {
      "OR": [
        {
          "property": "id",
          "value": %d,
          "operator": "eq"
        },
        {
          "property": "number",
          "value": 123,
          "operator": "le"
        }
    ]}""",
    1L
  );
  private static String userName = "user";

  @ParameterizedTest
  @MethodSource
  public void test(SearchScope scope, String selectionQuery) {
    var collection = new Collection(1L);
    collection.setShepardId(collection.getId());
    var dataObject = new DataObject(2L);
    dataObject.setShepardId(dataObject.getId());
    dataObject.setCollection(collection);
    SearchScope[] scopes = { scope };
    SearchParams searchParams = new SearchParams(query, QueryType.DataObject);
    SearchBody searchBody = new SearchBody(scopes, searchParams);
    when(searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY)).thenReturn(List.of(dataObject));
    ResultTriple resultTriple = new ResultTriple(1L, 2L);
    ResultTriple[] resultTriples = { resultTriple };
    BasicEntityIO[] results = { new BasicEntityIO(dataObject) };
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchParams);
    var actual = dataObjectSearcher.search(searchBody, userName);
    assertEquals(responseBody, actual);
  }

  private static Stream<? extends Arguments> test() {
    TraversalRules[] traversalRules = { TraversalRules.children };

    var scope1 = new SearchScope(null, null, new TraversalRules[0]);
    var scope2 = new SearchScope(1L, null, new TraversalRules[0]);
    var scope3 = new SearchScope(1L, 2L, new TraversalRules[0]);
    var scope4 = new SearchScope(1L, 2L, traversalRules);

    var query1 = Neo4jEmitter.emitDataObjectSelectionQuery(query, userName);
    var query2 = Neo4jEmitter.emitCollectionDataObjectSelectionQuery(1L, query, userName);
    var query3 = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(scope3, query, userName);
    var query4 = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(
      scope4,
      traversalRules[0],
      query,
      userName
    );

    // @formatter:off
		return Stream.of(
					Arguments.of(scope1, query1),
					Arguments.of(scope2, query2),
					Arguments.of(scope3, query3),
					Arguments.of(scope4, query4)
				);
    // @formatter:on
  }

  @Test
  public void test_invalid() {
    SearchScope[] scopes = { new SearchScope(null, 2L, new TraversalRules[0]) };
    SearchParams searchParams = new SearchParams(query, QueryType.DataObject);
    SearchBody searchBody = new SearchBody(scopes, searchParams);
    ResultTriple[] resultTriples = {};
    BasicEntityIO[] results = {};
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchParams);
    var actual = dataObjectSearcher.search(searchBody, userName);
    assertEquals(responseBody, actual);
  }
}
