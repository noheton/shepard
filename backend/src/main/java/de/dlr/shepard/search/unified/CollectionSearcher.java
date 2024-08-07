package de.dlr.shepard.search.unified;

import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.search.Neo4jEmitter;
import de.dlr.shepard.util.Constants;

public class CollectionSearcher implements ISearcher {

  private SearchDAO searchDAO = new SearchDAO();

  @Override
  public ResponseBody search(SearchBody searchBody, String userName) {
    String selectionQuery = Neo4jEmitter.emitCollectionSelectionQuery(
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
