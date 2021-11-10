package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.mongoDB.MongoDBConnector;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataReferenceDAO;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.util.TraversalRules;

public class StructuredDataSearcherTest extends BaseTestCase {

	@Mock
	private StructuredDataReferenceDAO structuredDataReferenceDAO;

	@Mock
	private BasicReferenceDAO basicReferenceDAO;

	@Mock
	private MongoDBConnector mongoDBConnector;

	@Mock
	private MongoDatabase mongoDatabase;

	@Mock
	private MongoCollection<Document> mongoContainer;

	@Mock
	private FindIterable<Document> mongoQueryResult;

	@Mock
	private Document firstDocument;

	@InjectMocks
	private StructuredDataSearcher structuredDataSearcher;

	@BeforeEach
	public void setupConnector() {
		when(mongoDBConnector.getDatabase()).thenReturn(mongoDatabase);
	}

	@Test
	public void getStructuredDataResponseTestEmpty() {
		// create StructuredDataReferences
		StructuredDataReference structuredDataReference1 = new StructuredDataReference() {
			{
				setDeleted(false);
				setId(1L);
				setName("reference1");
			}
		};
		StructuredData structuredData = new StructuredData();
		structuredData.setOid("61371f2889b108615688e22e");
		ArrayList<StructuredData> structuredDatas = new ArrayList<StructuredData>();
		structuredDatas.add(structuredData);
		structuredDataReference1.setStructuredDatas(structuredDatas);
		StructuredDataContainer structuredDataContainer1 = new StructuredDataContainer();
		String mongoID = "61371f2889b108615688e22e";
		structuredDataContainer1.setMongoId(mongoID);
		structuredDataContainer1.setId(2L);
		structuredDataReference1.setStructuredDataContainer(structuredDataContainer1);
		ArrayList<StructuredDataReference> emptyStructuredDataReferenceResponse = new ArrayList<StructuredDataReference>();
		emptyStructuredDataReferenceResponse.add(structuredDataReference1);
		// create SearchBody
		Long collectionId = 1L;
		Long dataObjectId = 2L;
		TraversalRules[] traversalRules = {};
		SearchScope scope = new SearchScope();
		scope.setCollectionId(collectionId);
		scope.setDataObjectId(dataObjectId);
		scope.setTraversalRules(traversalRules);
		SearchScope scopes[] = { scope };
		String query = "xwert: {$gt: 0}";
		QueryType queryType = QueryType.StructuredData;
		SearchParams searchParams = new SearchParams();
		searchParams.setQuery(query);
		searchParams.setQueryType(queryType);
		SearchBody searchBody = new SearchBody();
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		// create ResponseBody
		ResultTriple[] resultTriples = new ResultTriple[1];
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(searchBody.getScopes()[0].getCollectionId());
		resultTriple.setReferenceId(1L);
		resultTriple.setDataObjectId(3L);
		resultTriples[0] = resultTriple;
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchBody.getSearchParams());
		// configure Mocks
		when(structuredDataReferenceDAO.findReachableReferences(dataObjectId))
				.thenReturn(emptyStructuredDataReferenceResponse);
		when(mongoDatabase.getCollection(mongoID)).thenReturn(mongoContainer);
		when(mongoContainer.find(any(Document.class))).thenReturn(mongoQueryResult);
		when(mongoQueryResult.first()).thenReturn(firstDocument);
		when(basicReferenceDAO.getPredecessorDataObjectId(1L)).thenReturn(3L);
		// define expected values
		HashSet<StructuredDataReference> expectedRet = new HashSet<StructuredDataReference>();
		expectedRet.add(structuredDataReference1);
		// test
		assertEquals(structuredDataSearcher.search(searchBody), responseBody);
	}

	@Test
	public void getStructuredDataResponseTestNotEmpty() {
		// create StructuredDataReferences
		StructuredDataReference structuredDataReference1 = new StructuredDataReference() {
			{
				setDeleted(false);
				setId(1L);
				setName("reference1");
			}
		};
		StructuredData structuredData = new StructuredData();
		structuredData.setOid("61371f2889b108615688e22e");
		ArrayList<StructuredData> structuredDatas = new ArrayList<StructuredData>();
		structuredDatas.add(structuredData);
		structuredDataReference1.setStructuredDatas(structuredDatas);
		StructuredDataContainer structuredDataContainer1 = new StructuredDataContainer();
		String mongoID = "61371f2889b108615688e22e";
		structuredDataContainer1.setMongoId(mongoID);
		structuredDataContainer1.setId(2L);
		structuredDataReference1.setStructuredDataContainer(structuredDataContainer1);
		ArrayList<StructuredDataReference> emptyStructuredDataReferenceResponse = new ArrayList<StructuredDataReference>();
		emptyStructuredDataReferenceResponse.add(structuredDataReference1);
		// create SearchBody
		Long collectionId = 1L;
		Long dataObjectId = 2L;
		TraversalRules[] traversalRules = { TraversalRules.children };
		SearchScope scope = new SearchScope();
		scope.setCollectionId(collectionId);
		scope.setDataObjectId(dataObjectId);
		scope.setTraversalRules(traversalRules);
		SearchScope scopes[] = { scope };
		String query = "xwert: {$gt: 0}";
		QueryType queryType = QueryType.StructuredData;
		SearchParams searchParams = new SearchParams();
		searchParams.setQuery(query);
		searchParams.setQueryType(queryType);
		SearchBody searchBody = new SearchBody();
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		// create ResponseBody
		ResultTriple[] resultTriples = new ResultTriple[1];
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(searchBody.getScopes()[0].getCollectionId());
		resultTriple.setReferenceId(1L);
		resultTriple.setDataObjectId(3L);
		resultTriples[0] = resultTriple;
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchBody.getSearchParams());
		// configure Mocks
		when(structuredDataReferenceDAO.findReachableReferences(TraversalRules.children, dataObjectId))
				.thenReturn(emptyStructuredDataReferenceResponse);
		when(mongoDatabase.getCollection(mongoID)).thenReturn(mongoContainer);
		when(mongoContainer.find(any(Document.class))).thenReturn(mongoQueryResult);
		when(mongoQueryResult.first()).thenReturn(firstDocument);
		when(basicReferenceDAO.getPredecessorDataObjectId(1L)).thenReturn(3L);
		// define expected values
		HashSet<StructuredDataReference> expectedRet = new HashSet<StructuredDataReference>();
		expectedRet.add(structuredDataReference1);
		// test
		assertEquals(structuredDataSearcher.search(searchBody), responseBody);
	}

}
