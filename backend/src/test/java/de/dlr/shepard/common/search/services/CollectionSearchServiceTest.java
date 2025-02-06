package de.dlr.shepard.common.search.services;

import de.dlr.shepard.common.search.daos.SearchDAO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;

@QuarkusComponentTest
public class CollectionSearchServiceTest {

  @InjectMock
  SearchDAO searchDAO;

  @Inject
  CollectionSearchService collectionSearcher;
  /*@Test
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
    String selectionQuery = Neo4jQueryBuilder.collectionDataObjectSelectionQuery(
      searchBody.getSearchParams().getQuery(),
      userName
    );
    when(searchDAO.findCollections(selectionQuery, Constants.COLLECTION_IN_QUERY)).thenReturn(List.of(collection));
    ResultTriple[] resultTriples = { new ResultTriple(collection.getShepardId()) };
    BasicEntityIO[] results = { new BasicEntityIO(collection) };
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    var actual = collectionSearcher.search(searchBody, userName);
    assertEquals(responseBody, actual);
  }*/
}
