package de.dlr.shepard.common.search.unified;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.search.Neo4jEmitter;
import de.dlr.shepard.common.search.SearchDAO;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.entities.Collection;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class CollectionSearcherTest {

  @InjectMock
  SearchDAO searchDAO;

  @Inject
  CollectionSearcher collectionSearcher;

  @Test
  public void test() {
    String userName = "user1";
    var collection = new Collection(1L);
    collection.setShepardId(1L);
    SearchScope scopes[] = { new SearchScope() };
    String query = String.format(
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
    QueryType queryType = QueryType.Collection;
    SearchParams searchParams = new SearchParams(query, queryType);
    SearchBody searchBody = new SearchBody(scopes, searchParams);
    String selectionQuery = Neo4jEmitter.emitCollectionSelectionQuery(
      searchBody.getSearchParams().getQuery(),
      userName
    );
    when(searchDAO.findCollections(selectionQuery, Constants.COLLECTION_IN_QUERY)).thenReturn(List.of(collection));
    ResultTriple[] resultTriples = { new ResultTriple(collection.getShepardId()) };
    BasicEntityIO[] results = { new BasicEntityIO(collection) };
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    var actual = collectionSearcher.search(searchBody, userName);
    assertEquals(responseBody, actual);
  }
}
