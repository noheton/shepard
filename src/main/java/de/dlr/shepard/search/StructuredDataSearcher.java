package de.dlr.shepard.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.Document;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;

import de.dlr.shepard.mongoDB.MongoDBConnector;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataReferenceDAO;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.util.TraversalRules;

public class StructuredDataSearcher implements ISearcher {

	private StructuredDataReferenceDAO structuredDataReferenceDAO = new StructuredDataReferenceDAO();
	private BasicReferenceDAO basicReferenceDAO = new BasicReferenceDAO();
	private MongoDBConnector mongoDBConnector = MongoDBConnector.getInstance();

	@Override
	public ResponseBody search(SearchBody searchBody, String userName) {
		Set<StructuredDataReference> reachableReferences = getAllStructuredDataReferencesFromBody(searchBody, userName);
		return getStructuredDataResponse(reachableReferences, searchBody);
	}

	private ResponseBody getStructuredDataResponse(Set<StructuredDataReference> reachableReferences,
			SearchBody searchBody) {
		Set<Long> matchingReferencesIds = new HashSet<>();
		String mongoContainerId;
		MongoCollection<Document> mongoContainer;
		List<StructuredData> structuredDatas;
		List<String> mongoStructuredDataIds;
		String mongoStructuredDataId;
		String mongoQuery;
		Document mongoQueryDocument;
		FindIterable<Document> mongoQueryResult;
		for (StructuredDataReference reference : reachableReferences) {
			mongoContainerId = reference.getStructuredDataContainer().getMongoId();
			mongoContainer = mongoDBConnector.getDatabase().getCollection(mongoContainerId);
			structuredDatas = reference.getStructuredDatas();
			mongoStructuredDataIds = new ArrayList<>();
			for (StructuredData structuredData : structuredDatas) {
				mongoStructuredDataId = structuredData.getOid();
				mongoStructuredDataIds.add(makeMongoQueryId(mongoStructuredDataId));
			}
			mongoQuery = "{_id: {$in: " + makeMongoQueryArray(mongoStructuredDataIds) + "}";
			String mongoSearchQuery = searchBody.getSearchParams().getQuery();
			// JSON queries start with { so they have to be translated to MongoDB syntax
			// first
			if (mongoSearchQuery.startsWith("{"))
				mongoSearchQuery = MongoDBEmitter.emitMongoDB(mongoSearchQuery);
			mongoQuery = mongoQuery + ", " + mongoSearchQuery + "}";
			mongoQueryDocument = Document.parse(mongoQuery);
			mongoQueryResult = mongoContainer.find(mongoQueryDocument);
			if (mongoQueryResult.first() != null)
				matchingReferencesIds.add(reference.getId());
		}
		ResultTriple[] resultTriples = new ResultTriple[matchingReferencesIds.size()];
		int i = 0;
		for (Long matchingReferenceId : matchingReferencesIds) {
			ResultTriple resultTriple = new ResultTriple(searchBody.getScopes()[0].getCollectionId(),
					basicReferenceDAO.getDataObjectId(matchingReferenceId), matchingReferenceId);
			resultTriples[i] = resultTriple;
			i++;
		}
		ResponseBody responseBody = new ResponseBody(resultTriples, searchBody.getSearchParams());
		return responseBody;
	}

	private Set<StructuredDataReference> getAllStructuredDataReferencesFromBody(SearchBody searchBody,
			String userName) {
		Set<StructuredDataReference> ret = new HashSet<>();
		SearchScope[] searchScopes = searchBody.getScopes();
		for (SearchScope searchScope : searchScopes)
			ret.addAll(getAllStructuredaDataReferencesFromScope(searchScope, userName));
		return ret;
	}

	private Set<StructuredDataReference> getAllStructuredaDataReferencesFromScope(SearchScope searchScope,
			String userName) {
		Set<StructuredDataReference> ret = new HashSet<>();
		TraversalRules[] traversalRules = searchScope.getTraversalRules();
		long startId = searchScope.getDataObjectId();
		long collectionId = searchScope.getCollectionId();
		if (traversalRules.length == 0) {
			List<StructuredDataReference> reachableReferences = structuredDataReferenceDAO
					.findReachableReferences(collectionId, startId, userName);
			ret.addAll(reachableReferences);
		} else {
			for (TraversalRules traversalRule : traversalRules) {
				List<StructuredDataReference> reachableReferences = structuredDataReferenceDAO
						.findReachableReferences(traversalRule, collectionId, startId, userName);
				ret.addAll(reachableReferences);
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
