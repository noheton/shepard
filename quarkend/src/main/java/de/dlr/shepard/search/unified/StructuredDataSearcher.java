package de.dlr.shepard.search.unified;

import com.mongodb.client.MongoCollection;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.MongoDBConnector;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.neo4Core.dao.StructuredDataReferenceDAO;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.search.MongoDBEmitter;
import de.dlr.shepard.util.TraversalRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;

public class StructuredDataSearcher implements ISearcher {

  private StructuredDataReferenceDAO structuredDataReferenceDAO = new StructuredDataReferenceDAO();
  private MongoDBConnector mongoDBConnector = MongoDBConnector.getInstance();

  @Override
  public ResponseBody search(SearchBody searchBody, String userName) {
    var reachableReferences = findReachableReferences(searchBody, userName);
    var matchingReferences = findMatchingReferences(reachableReferences, searchBody);
    return getStructuredDataResponse(matchingReferences, searchBody);
  }

  private ResponseBody getStructuredDataResponse(
    Map<StructuredDataReference, Long> matchingReferences,
    SearchBody searchBody
  ) {
    BasicReference[] references = matchingReferences.keySet().toArray(new BasicReference[0]);
    ResultTriple[] resultTriples = new ResultTriple[references.length];
    BasicEntityIO[] results = new BasicEntityIO[references.length];
    for (var i = 0; i < references.length; i++) {
      // The collection is not loaded at this time, so we have to use the given id
      resultTriples[i] = new ResultTriple(
        matchingReferences.get(references[i]),
        references[i].getDataObject().getId(),
        references[i].getId()
      );
      results[i] = new BasicEntityIO(references[i]);
    }
    ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
    return responseBody;
  }

  private Map<StructuredDataReference, Long> findMatchingReferences(
    Map<StructuredDataReference, Long> reachableReferences,
    SearchBody searchBody
  ) {
    Map<StructuredDataReference, Long> matchingReferences = new HashMap<>();
    for (var reference : reachableReferences.entrySet()) {
      String mongoContainerId = reference.getKey().getStructuredDataContainer().getMongoId();
      MongoCollection<Document> mongoContainer = mongoDBConnector.getDatabase().getCollection(mongoContainerId);
      List<String> mongoStructuredDataIds = new ArrayList<>();
      for (StructuredData structuredData : reference.getKey().getStructuredDatas()) {
        mongoStructuredDataIds.add(makeMongoQueryId(structuredData.getOid()));
      }
      String mongoQuery = "{_id: {$in: " + makeMongoQueryArray(mongoStructuredDataIds) + "}";
      String mongoSearchQuery = searchBody.getSearchParams().getQuery();
      // JSON queries start with a curly bracket ({) so they have to be translated to
      // MongoDB syntax first
      // TODO: Deprecate MongoDB queries
      if (mongoSearchQuery.startsWith("{")) mongoSearchQuery = MongoDBEmitter.emitMongoDB(mongoSearchQuery);
      mongoQuery += ", " + mongoSearchQuery + "}";
      var mongoQueryDocument = Document.parse(mongoQuery);
      var mongoQueryResult = mongoContainer.find(mongoQueryDocument);
      if (mongoQueryResult.first() != null) matchingReferences.put(reference.getKey(), reference.getValue());
    }
    return matchingReferences;
  }

  private Map<StructuredDataReference, Long> findReachableReferences(SearchBody searchBody, String userName) {
    Map<StructuredDataReference, Long> ret = new HashMap<>();
    for (SearchScope searchScope : searchBody.getScopes()) findReachableReferenceFromScope(
      searchScope,
      userName
    ).forEach(r -> ret.put(r, searchScope.getCollectionId()));
    return ret;
  }

  private Set<StructuredDataReference> findReachableReferenceFromScope(SearchScope searchScope, String userName) {
    Set<StructuredDataReference> ret = new HashSet<>();
    TraversalRules[] traversalRules = searchScope.getTraversalRules();
    Long collectionShepardId = searchScope.getCollectionId();
    if (collectionShepardId == null) {
      throw new InvalidBodyException("Collection is necessary");
    }
    // no DataObjectId given
    if (searchScope.getDataObjectId() == null) {
      ret.addAll(structuredDataReferenceDAO.findReachableReferencesByShepardId(collectionShepardId, userName));
    }
    // DataObjectId given
    else {
      long startShepardId = searchScope.getDataObjectId();
      // consider only start node
      if (traversalRules.length == 0) {
        List<StructuredDataReference> reachableReferences =
          structuredDataReferenceDAO.findReachableReferencesByShepardId(collectionShepardId, startShepardId, userName);
        ret.addAll(reachableReferences);
      }
      // search according to traversal rules
      else {
        for (TraversalRules traversalRule : traversalRules) {
          List<StructuredDataReference> reachableReferences =
            structuredDataReferenceDAO.findReachableReferencesByShepardId(
              traversalRule,
              collectionShepardId,
              startShepardId,
              userName
            );
          ret.addAll(reachableReferences);
        }
      }
    }
    return ret;
  }

  private static String makeMongoQueryId(String mongoId) {
    return "{$oid: '" + mongoId + "'}";
  }

  private static String makeMongoQueryArray(List<String> strings) {
    return "[" + String.join(", ", strings) + "]";
  }
}
