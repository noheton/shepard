package de.dlr.shepard.common.search.services;

import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.ResultTriple;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class CollectionSearcher {

  private SearchDAO searchDAO;

  CollectionSearcher() {}

  @Inject
  public CollectionSearcher(SearchDAO searchDAO) {
    this.searchDAO = searchDAO;
  }

  public ResponseBody search(SearchBody searchBody, String userName) {
    String selectionQuery = Neo4jQueryBuilder.collectionSelectionQuery(
      searchBody.getSearchParams().getQuery(),
      userName
    );
    var resultList = searchDAO.findCollections(selectionQuery, Constants.COLLECTION_IN_QUERY);
    ResultTriple[] resultTriples = new ResultTriple[resultList.size()];
    BasicEntityIO[] results = new BasicEntityIO[resultList.size()];
    for (int i = 0; i < resultList.size(); i++) {
      resultTriples[i] = new ResultTriple(resultList.get(i).getShepardId());
      results[i] = new BasicEntityIO(resultList.get(i));
    }
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    return responseBody;
  }
}
