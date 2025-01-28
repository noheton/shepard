package de.dlr.shepard.common.search.services;

import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.ResultTriple;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.TraversalRules;
import de.dlr.shepard.context.collection.entities.DataObject;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.Set;

@RequestScoped
public class DataObjectSearchService {

  private SearchDAO searchDAO;

  DataObjectSearchService() {}

  @Inject
  public DataObjectSearchService(SearchDAO searchDAO) {
    this.searchDAO = searchDAO;
  }

  public ResponseBody search(SearchBody searchBody, String userName) {
    Set<DataObject> resultsSet = new HashSet<>();
    SearchScope[] scopes = searchBody.getScopes();
    String searchBodyQuery = searchBody.getSearchParams().getQuery();
    for (SearchScope scope : scopes) {
      // no CollectionId and no DataObjectId given
      if (scope.getCollectionId() == null && scope.getDataObjectId() == null) {
        String selectionQuery = Neo4jQueryBuilder.dataObjectSelectionQuery(searchBodyQuery, userName);
        var res = searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
        resultsSet.addAll(res);
      }
      // CollectionId given but no DataObjectId
      else if (scope.getCollectionId() != null && scope.getDataObjectId() == null) {
        String selectionQuery = Neo4jQueryBuilder.collectionDataObjectSelectionQuery(
          scope.getCollectionId(),
          searchBodyQuery,
          userName
        );
        var res = searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
        resultsSet.addAll(res);
      }
      // CollectionId and DataObjectId given
      else if (scope.getCollectionId() != null && scope.getDataObjectId() != null) {
        // search according to TraversalRules
        if (scope.getTraversalRules().length != 0) {
          for (TraversalRules traversalRules : scope.getTraversalRules()) {
            String selectionQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQuery(
              scope,
              traversalRules,
              searchBodyQuery,
              userName
            );
            var res = searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
            resultsSet.addAll(res);
          }
        }
        // no TraversalRules given
        else {
          String selectionQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQuery(
            scope,
            searchBodyQuery,
            userName
          );
          var res = searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
          resultsSet.addAll(res);
        }
      }
    }
    DataObject[] dataObjects = resultsSet.toArray(new DataObject[0]);
    ResultTriple[] resultTriples = new ResultTriple[resultsSet.size()];
    BasicEntityIO[] results = new BasicEntityIO[resultsSet.size()];
    for (var i = 0; i < resultsSet.size(); i++) {
      resultTriples[i] = new ResultTriple(dataObjects[i].getCollection().getShepardId(), dataObjects[i].getShepardId());
      results[i] = new BasicEntityIO(dataObjects[i]);
    }
    ResponseBody ret = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    return ret;
  }
}
