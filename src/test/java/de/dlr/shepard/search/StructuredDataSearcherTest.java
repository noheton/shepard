package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.MongoDBConnector;
import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.neo4Core.dao.BasicReferenceDAO;
import de.dlr.shepard.neo4Core.dao.StructuredDataReferenceDAO;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.search.unified.QueryType;
import de.dlr.shepard.search.unified.ResponseBody;
import de.dlr.shepard.search.unified.ResultTriple;
import de.dlr.shepard.search.unified.SearchBody;
import de.dlr.shepard.search.unified.SearchParams;
import de.dlr.shepard.search.unified.SearchScope;
import de.dlr.shepard.search.unified.StructuredDataSearcher;
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
	public void getStructuredDataResponseTest() {
		Long collectionId = 1L;
		Long dataObjectId = 2L;
		String mongoID = "61371f2889b108615688e22e";
		// create StructuredDataReferences
		DataObject dataObject = new DataObject(dataObjectId);
		List<StructuredData> structuredDatas = List
				.of(new StructuredData("61371f2889b108615688e22e", new Date(), "name"));
		StructuredDataContainer sdContainer = new StructuredDataContainer(2L);
		sdContainer.setMongoId(mongoID);
		StructuredDataReference sdReference = new StructuredDataReference() {
			{
				setId(3L);
				setDeleted(false);
				setName("reference1");
				setStructuredDatas(structuredDatas);
				setStructuredDataContainer(sdContainer);
				setDataObject(dataObject);
			}
		};
		// create SearchBody
		TraversalRules[] traversalRules = {};
		SearchScope scope = new SearchScope(collectionId, dataObjectId, traversalRules);
		SearchScope scopes[] = { scope };
		String query = "xwert: {$gt: 0}";
		QueryType queryType = QueryType.StructuredData;
		SearchParams searchParams = new SearchParams(query, queryType);
		SearchBody searchBody = new SearchBody(scopes, searchParams);
		// create ResponseBody
		ResultTriple resultTriple = new ResultTriple(1L, 2L, 3L);
		ResultTriple[] resultTriples = { resultTriple };
		BasicEntityIO[] results = { new BasicEntityIO(sdReference) };
		ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
		// configure Mocks
		when(structuredDataReferenceDAO.findReachableReferences(collectionId, dataObjectId, "user1"))
				.thenReturn(List.of(sdReference));
		when(mongoDatabase.getCollection(mongoID)).thenReturn(mongoContainer);
		when(mongoContainer.find(any(Document.class))).thenReturn(mongoQueryResult);
		when(mongoQueryResult.first()).thenReturn(firstDocument);
		// test
		var actual = structuredDataSearcher.search(searchBody, "user1");
		assertEquals(responseBody, actual);
	}

	@Test
	public void getStructuredDataResponseTest_TraversalRules() {
		Long collectionId = 1L;
		Long dataObjectId = 2L;
		String mongoID = "61371f2889b108615688e22e";
		// create StructuredDataReferences
		DataObject dataObject = new DataObject(dataObjectId);
		List<StructuredData> structuredDatas = List
				.of(new StructuredData("61371f2889b108615688e22e", new Date(), "name"));
		StructuredDataContainer sdContainer = new StructuredDataContainer(2L);
		sdContainer.setMongoId(mongoID);
		StructuredDataReference sdReference = new StructuredDataReference() {
			{
				setId(3L);
				setDeleted(false);
				setName("reference1");
				setStructuredDatas(structuredDatas);
				setStructuredDataContainer(sdContainer);
				setDataObject(dataObject);
			}
		};
		// create SearchBody
		TraversalRules[] traversalRules = { TraversalRules.children };
		SearchScope scope = new SearchScope(collectionId, dataObjectId, traversalRules);
		SearchScope scopes[] = { scope };
		String query = "xwert: {$gt: 0}";
		QueryType queryType = QueryType.StructuredData;
		SearchParams searchParams = new SearchParams(query, queryType);
		SearchBody searchBody = new SearchBody(scopes, searchParams);
		// create ResponseBody
		ResultTriple resultTriple = new ResultTriple(1L, 2L, 3L);
		ResultTriple[] resultTriples = { resultTriple };
		BasicEntityIO[] results = { new BasicEntityIO(sdReference) };
		ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
		// configure Mocks
		when(structuredDataReferenceDAO.findReachableReferences(TraversalRules.children, collectionId, dataObjectId,
				"user1")).thenReturn(List.of(sdReference));
		when(mongoDatabase.getCollection(mongoID)).thenReturn(mongoContainer);
		when(mongoContainer.find(any(Document.class))).thenReturn(mongoQueryResult);
		when(mongoQueryResult.first()).thenReturn(firstDocument);
		// test
		var actual = structuredDataSearcher.search(searchBody, "user1");
		assertEquals(responseBody, actual);
	}

	@Test
	public void getStructuredDataResponseTest_JsonQuery() {
		Long collectionId = 1L;
		Long dataObjectId = 2L;
		String mongoID = "61371f2889b108615688e22e";
		// create StructuredDataReferences
		DataObject dataObject = new DataObject(dataObjectId);
		List<StructuredData> structuredDatas = List
				.of(new StructuredData("61371f2889b108615688e22e", new Date(), "name"));
		StructuredDataContainer sdContainer = new StructuredDataContainer(2L);
		sdContainer.setMongoId(mongoID);
		StructuredDataReference sdReference = new StructuredDataReference() {
			{
				setId(3L);
				setDeleted(false);
				setName("reference1");
				setStructuredDatas(structuredDatas);
				setStructuredDataContainer(sdContainer);
				setDataObject(dataObject);
			}
		};
		// create SearchBody
		TraversalRules[] traversalRules = {};
		SearchScope scope = new SearchScope(collectionId, dataObjectId, traversalRules);
		SearchScope scopes[] = { scope };
		String query = """
				{
				  "property": "name",
				  "value": "MyName",
				  "operator": "eq"
				}
				""";
		QueryType queryType = QueryType.StructuredData;
		SearchParams searchParams = new SearchParams(query, queryType);
		SearchBody searchBody = new SearchBody(scopes, searchParams);
		// create ResponseBody
		ResultTriple resultTriple = new ResultTriple(1L, 2L, 3L);
		ResultTriple[] resultTriples = { resultTriple };
		BasicEntityIO[] results = { new BasicEntityIO(sdReference) };
		ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
		// configure Mocks
		when(structuredDataReferenceDAO.findReachableReferences(collectionId, dataObjectId, "user1"))
				.thenReturn(List.of(sdReference));
		when(mongoDatabase.getCollection(mongoID)).thenReturn(mongoContainer);
		when(mongoContainer.find(any(Document.class))).thenReturn(mongoQueryResult);
		when(mongoQueryResult.first()).thenReturn(firstDocument);
		// test
		var actual = structuredDataSearcher.search(searchBody, "user1");
		assertEquals(responseBody, actual);
	}

	@Test
	public void getStructuredDataResponseTest_NoReferences() {
		// create SearchBody
		Long collectionId = 1L;
		Long dataObjectId = 2L;
		TraversalRules[] traversalRules = {};
		SearchScope scope = new SearchScope(collectionId, dataObjectId, traversalRules);
		SearchScope scopes[] = { scope };
		String query = "xwert: {$gt: 0}";
		QueryType queryType = QueryType.StructuredData;
		SearchParams searchParams = new SearchParams(query, queryType);
		SearchBody searchBody = new SearchBody(scopes, searchParams);
		// create ResponseBody
		ResultTriple[] resultTriples = {};
		BasicEntityIO[] results = {};
		ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
		// configure Mocks
		when(structuredDataReferenceDAO.findReachableReferences(collectionId, dataObjectId, "user1"))
				.thenReturn(Collections.emptyList());
		// test
		var actual = structuredDataSearcher.search(searchBody, "user1");
		assertEquals(responseBody, actual);
	}

	@Test
	public void getStructuredDataResponseTest_NoMatches() {
		Long collectionId = 1L;
		Long dataObjectId = 2L;
		String mongoID = "61371f2889b108615688e22e";
		// create StructuredDataReferences
		DataObject dataObject = new DataObject(dataObjectId);
		List<StructuredData> structuredDatas = List
				.of(new StructuredData("61371f2889b108615688e22e", new Date(), "name"));
		StructuredDataContainer sdContainer = new StructuredDataContainer(2L);
		sdContainer.setMongoId(mongoID);
		StructuredDataReference sdReference = new StructuredDataReference() {
			{
				setId(3L);
				setDeleted(false);
				setName("reference1");
				setStructuredDatas(structuredDatas);
				setStructuredDataContainer(sdContainer);
				setDataObject(dataObject);
			}
		};
		// create SearchBody
		TraversalRules[] traversalRules = {};
		SearchScope scope = new SearchScope(collectionId, dataObjectId, traversalRules);
		SearchScope scopes[] = { scope };
		String query = "xwert: {$gt: 0}";
		QueryType queryType = QueryType.StructuredData;
		SearchParams searchParams = new SearchParams(query, queryType);
		SearchBody searchBody = new SearchBody(scopes, searchParams);
		// create ResponseBody
		ResultTriple[] resultTriples = {};
		BasicEntityIO[] results = {};
		ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
		// configure Mocks
		when(structuredDataReferenceDAO.findReachableReferences(collectionId, dataObjectId, "user1"))
				.thenReturn(List.of(sdReference));
		when(mongoDatabase.getCollection(mongoID)).thenReturn(mongoContainer);
		when(mongoContainer.find(any(Document.class))).thenReturn(mongoQueryResult);
		when(mongoQueryResult.first()).thenReturn(null);
		// test
		var actual = structuredDataSearcher.search(searchBody, "user1");
		assertEquals(responseBody, actual);
	}

	@ParameterizedTest
	@CsvSource({ ",", "1,", ",2" })
	public void getStructuredDataResponseTest_noIds(Long collectionId, Long dataObjectId) {
		// create SearchBody
		TraversalRules[] traversalRules = {};
		SearchScope scope = new SearchScope(collectionId, dataObjectId, traversalRules);
		SearchScope scopes[] = { scope };
		String query = "xwert: {$gt: 0}";
		QueryType queryType = QueryType.StructuredData;
		SearchParams searchParams = new SearchParams(query, queryType);
		SearchBody searchBody = new SearchBody(scopes, searchParams);
		// test
		assertThrows(InvalidBodyException.class, () -> structuredDataSearcher.search(searchBody, "user1"));
	}
}
