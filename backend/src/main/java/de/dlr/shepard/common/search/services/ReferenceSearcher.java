package de.dlr.shepard.common.search.services;

import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.ResultTriple;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.Set;

@RequestScoped
public class ReferenceSearcher {

  private SearchDAO searchDAO;

  ReferenceSearcher() {}

  @Inject
  public ReferenceSearcher(SearchDAO searchDAO) {
    this.searchDAO = searchDAO;
  }

  public ResponseBody search(SearchBody searchBody, String userName) {
    Set<BasicReference> resultsSet = new HashSet<>();
    SearchScope[] scopes = searchBody.getScopes();
    String searchBodyQuery = searchBody.getSearchParams().getQuery();
    for (SearchScope scope : scopes) {
      // no CollectionId and no DataObjectId given
      if (scope.getCollectionId() == null && scope.getDataObjectId() == null) {
        String selectionQuery = Neo4jQueryBuilder.basicReferenceSelectionQuery(searchBodyQuery, userName);
        var res = searchDAO.findReferences(selectionQuery, Constants.REFERENCE_IN_QUERY);
        resultsSet.addAll(res);
      }
      // CollectionId given but no DataObjectId
      else if (scope.getCollectionId() != null && scope.getDataObjectId() == null) {
        String selectionQuery = Neo4jQueryBuilder.collectionBasicReferenceSelectionQuery(
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
            String selectionQuery = Neo4jQueryBuilder.collectionDataObjectBasicReferenceSelectionQuery(
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
          String selectionQuery = Neo4jQueryBuilder.collectionDataObjectReferenceSelectionQuery(
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
