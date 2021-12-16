package de.dlr.shepard.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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
	public ResponseBody search(SearchBody searchBody) {
		HashSet<StructuredDataReference> reachableReferences = getAllStructuredDataReferencesFromBody(searchBody);
		return getStructuredDataResponse(reachableReferences, searchBody);
	}

	private ResponseBody getStructuredDataResponse(HashSet<StructuredDataReference> reachableReferences,
			SearchBody searchBody) {
		HashSet<Long> matchingReferencesIds = new HashSet<Long>();
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
			mongoStructuredDataIds = new ArrayList<String>();
			for (StructuredData structuredData : structuredDatas) {
				mongoStructuredDataId = structuredData.getOid();
				mongoStructuredDataIds.add(makeMongoQueryId(mongoStructuredDataId));
			}
			mongoQuery = "{_id: {$in: " + makeMongoQueryArray(mongoStructuredDataIds) + "}";
			mongoQuery = mongoQuery + ", " + searchBody.getSearchParams().getQuery() + "}";
			mongoQueryDocument = Document.parse(mongoQuery);
			mongoQueryResult = mongoContainer.find(mongoQueryDocument);
			if (mongoQueryResult.first() != null)
				matchingReferencesIds.add(reference.getId());
		}
		ResultTriple[] resultTriples = new ResultTriple[matchingReferencesIds.size()];
		int i = 0;
		for (Long matchingReferenceId : matchingReferencesIds) {
			ResultTriple resultTriple = new ResultTriple();
			resultTriple.setCollectionId(searchBody.getScopes()[0].getCollectionId());
			resultTriple.setReferenceId(matchingReferenceId);
			resultTriple.setDataObjectId(basicReferenceDAO.getDataObjectId(matchingReferenceId));
			resultTriples[i] = resultTriple;
			i++;
		}
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchBody.getSearchParams());
		return responseBody;

	}

	private HashSet<StructuredDataReference> getAllStructuredDataReferencesFromBody(SearchBody searchBody) {
		HashSet<StructuredDataReference> ret = new HashSet<StructuredDataReference>();
		SearchScope[] searchScopes = searchBody.getScopes();
		for (SearchScope searchScope : searchScopes)
			ret.addAll(getAllStructuredaDataReferencesFromScope(searchScope));
		System.out.println("ret.size: " + ret.size());
		return ret;
	}

	private HashSet<StructuredDataReference> getAllStructuredaDataReferencesFromScope(SearchScope searchScope) {
		HashSet<StructuredDataReference> ret = new HashSet<StructuredDataReference>();
		TraversalRules[] traversalRules = searchScope.getTraversalRules();
		long startId = searchScope.getDataObjectId();
		if (traversalRules.length == 0) {
			List<StructuredDataReference> reachableReferences = structuredDataReferenceDAO
					.findReachableReferences(startId);
			ret.addAll(reachableReferences);
		} else {
			for (TraversalRules traversalRule : traversalRules) {
				List<StructuredDataReference> reachableReferences = structuredDataReferenceDAO
						.findReachableReferences(traversalRule, startId);
				ret.addAll(reachableReferences);
			}
		}
		return ret;
	}

	private static String makeMongoQueryId(String mongoId) {
		return "{$oid: '" + mongoId + "'}";
	}

	private static String makeMongoQueryArray(List<String> strings) {
		var ret = "[" + String.join(", ", strings) + "]";
		return ret;
	}

}
