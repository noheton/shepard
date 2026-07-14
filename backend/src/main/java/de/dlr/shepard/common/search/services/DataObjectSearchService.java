package de.dlr.shepard.common.search.services;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.search.daos.SearchDAO;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.ResultTriple;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.common.search.query.Neo4jQuery;
import de.dlr.shepard.common.search.query.Neo4jQueryBuilder;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.TraversalRules;
import de.dlr.shepard.context.collection.entities.DataObject;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequestScoped
public class DataObjectSearchService {

  @Inject
  SearchDAO searchDAO;

  @Inject
  UserService userService;

  /**
   * Returns the count of matching DataObjects for a search body. Only global and single-collection
   * scopes are counted; traversal-rule scopes are ignored (they are not used by SearchV2Rest).
   */
  public int count(SearchBody searchBody) {
    User user = userService.getCurrentUser();
    int total = 0;
    for (SearchScope scope : searchBody.getScopes()) {
      Neo4jQuery selectionQuery = buildSelectionQuery(scope, searchBody.getSearchParams().getQuery(), user.getUsername());
      if (selectionQuery != null) {
        total += searchDAO.countDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
      }
    }
    return total;
  }

  /**
   * Returns a page of DataObjects at (skip, limit). Only supports single-scope search bodies
   * (the shape used by SearchV2Rest). Results are ordered by appId ASC for stable pagination.
   *
   * @throws IllegalStateException when the body contains more than one scope.
   */
  public ResponseBody searchPaged(SearchBody searchBody, int skip, int limit) {
    User user = userService.getCurrentUser();
    SearchScope[] scopes = searchBody.getScopes();
    if (scopes.length != 1) {
      throw new IllegalStateException("searchPaged requires exactly one scope; got " + scopes.length);
    }
    Neo4jQuery selectionQuery = buildSelectionQuery(scopes[0], searchBody.getSearchParams().getQuery(), user.getUsername());
    if (selectionQuery == null) {
      return new ResponseBody(new ResultTriple[0], new BasicEntityIO[0], searchBody.getSearchParams());
    }
    List<DataObject> dataObjects = searchDAO.findDataObjectsSlice(selectionQuery, Constants.DATAOBJECT_IN_QUERY, skip, limit);
    ResultTriple[] triples = new ResultTriple[dataObjects.size()];
    BasicEntityIO[] results = new BasicEntityIO[dataObjects.size()];
    for (int i = 0; i < dataObjects.size(); i++) {
      triples[i] = new ResultTriple(dataObjects.get(i).getCollection().getShepardId(), dataObjects.get(i).getShepardId());
      results[i] = new BasicEntityIO(dataObjects.get(i));
    }
    return new ResponseBody(triples, results, searchBody.getSearchParams());
  }

  private Neo4jQuery buildSelectionQuery(SearchScope scope, String query, String username) {
    if (scope.getCollectionId() == null && scope.getDataObjectId() == null) {
      return Neo4jQueryBuilder.dataObjectSelectionQueryWithNeo4jId(query, username);
    } else if (scope.getCollectionId() != null && scope.getDataObjectId() == null) {
      return Neo4jQueryBuilder.collectionDataObjectSelectionQueryWithNeo4jId(scope.getCollectionId(), query, username);
    }
    return null; // traversal-rule scopes unsupported in count/searchPaged
  }

  public ResponseBody search(SearchBody searchBody) {
    User user = userService.getCurrentUser();

    Set<DataObject> resultsSet = new HashSet<>();
    SearchScope[] scopes = searchBody.getScopes();
    String searchBodyQuery = searchBody.getSearchParams().getQuery();
    for (SearchScope scope : scopes) {
      // no CollectionId and no DataObjectId given
      if (scope.getCollectionId() == null && scope.getDataObjectId() == null) {
        Neo4jQuery selectionQuery = Neo4jQueryBuilder.dataObjectSelectionQueryWithNeo4jId(
          searchBodyQuery,
          user.getUsername()
        );
        var res = searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
        resultsSet.addAll(res);
      }
      // CollectionId given but no DataObjectId
      else if (scope.getCollectionId() != null && scope.getDataObjectId() == null) {
        Neo4jQuery selectionQuery = Neo4jQueryBuilder.collectionDataObjectSelectionQueryWithNeo4jId(
          scope.getCollectionId(),
          searchBodyQuery,
          user.getUsername()
        );
        var res = searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
        resultsSet.addAll(res);
      }
      // CollectionId and DataObjectId given
      else if (scope.getCollectionId() != null && scope.getDataObjectId() != null) {
        // search according to TraversalRules
        if (scope.getTraversalRules().length != 0) {
          for (TraversalRules traversalRules : scope.getTraversalRules()) {
            Neo4jQuery selectionQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
              scope,
              traversalRules,
              searchBodyQuery,
              user.getUsername()
            );
            var res = searchDAO.findDataObjects(selectionQuery, Constants.DATAOBJECT_IN_QUERY);
            resultsSet.addAll(res);
          }
        }
        // no TraversalRules given
        else {
          Neo4jQuery selectionQuery = Neo4jQueryBuilder.collectionDataObjectDataObjectSelectionQueryWithNeo4jId(
            scope,
            searchBodyQuery,
            user.getUsername()
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
