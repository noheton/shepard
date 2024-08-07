package de.dlr.shepard.search.unified;

import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.search.Neo4jEmitter;
import de.dlr.shepard.util.Constants;
import java.util.HashSet;
import java.util.Set;

public class ReferenceSearcher implements ISearcher {

  private SearchDAO searchDAO = new SearchDAO();

  @Override
  public ResponseBody search(SearchBody searchBody, String userName) {
    Set<BasicReference> resultsSet = new HashSet<>();
    SearchScope[] scopes = searchBody.getScopes();
    String searchBodyQuery = searchBody.getSearchParams().getQuery();
    for (SearchScope scope : scopes) {
      // no CollectionId and no DataObjectId given
      if (scope.getCollectionId() == null && scope.getDataObjectId() == null) {
        String selectionQuery = Neo4jEmitter.emitBasicReferenceSelectionQuery(searchBodyQuery, userName);
        var res = searchDAO.findReferences(selectionQuery, Constants.REFERENCE_IN_QUERY);
        resultsSet.addAll(res);
      }
      // CollectionId given but no DataObjectId
      else if (scope.getCollectionId() != null && scope.getDataObjectId() == null) {
        String selectionQuery = Neo4jEmitter.emitCollectionBasicReferenceSelectionQuery(
          searchBodyQuery,
          scope.getCollectionId(),
          userName
        );
        var res = searchDAO.findReferences(selectionQuery, Constants.REFERENCE_IN_QUERY);
        resultsSet.addAll(res);
      }
      // CollectionId and DataObjectId given
      else if (scope.getCollectionId() != null && scope.getDataObjectId() != null) {
        // search according to TraversalRules
        if (scope.getTraversalRules().length != 0) {
          for (int j = 0; j < scope.getTraversalRules().length; j++) {
            String selectionQuery = Neo4jEmitter.emitCollectionDataObjectBasicReferenceSelectionQuery(
              scope,
              scope.getTraversalRules()[j],
              searchBodyQuery,
              userName
            );
            var res = searchDAO.findReferences(selectionQuery, Constants.REFERENCE_IN_QUERY);
            resultsSet.addAll(res);
          }
        }
        // no TraversalRules given
        else {
          String selectionQuery = Neo4jEmitter.emitCollectionDataObjectReferenceSelectionQuery(
            scope,
            searchBodyQuery,
            userName
          );
          var res = searchDAO.findReferences(selectionQuery, Constants.REFERENCE_IN_QUERY);
          resultsSet.addAll(res);
        }
      }
    }
    BasicReference[] references = resultsSet.toArray(new BasicReference[0]);
    ResultTriple[] resultTriples = new ResultTriple[resultsSet.size()];
    BasicEntityIO[] results = new BasicEntityIO[resultsSet.size()];
    for (var i = 0; i < resultsSet.size(); i++) {
      resultTriples[i] = new ResultTriple(
        references[i].getDataObject().getCollection().getShepardId(),
        references[i].getDataObject().getShepardId(),
        references[i].getShepardId()
      );
      results[i] = new BasicEntityIO(references[i]);
    }
    ResponseBody ret = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    return ret;
  }
}
