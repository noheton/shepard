package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.TraversalRules;

public class DataObjectSearcherTest extends BaseTestCase {

	@Mock
	private SearchDAO searchDAO;

	@InjectMocks
	private DataObjectSearcher dataObjectSearcher;

	private static final String[] coldovariables = { Constants.COLLECTION_IN_QUERY, Constants.DATAOBJECT_IN_QUERY };
	private static final String[] dovariables = { Constants.DATAOBJECT_IN_QUERY };
	private static final Long collectionId1L = 1L;
	private static String query = String.format("""
			{
			  "OR": [
			    {
			      "property": "id",
			      "value": %d,
			      "operator": "eq"
			    },
			    {
			      "property": "number",
			      "value": 123,
			      "operator": "le"
			    }
			]}""", collectionId1L);
	private static String userName = "user";

	@Test
	public void collectionIdNullDataObjectIdNullTest() {
		SearchBody searchBody = new SearchBody();
		SearchScope scope = new SearchScope();
		scope.setCollectionId(null);
		scope.setDataObjectId(null);
		TraversalRules[] traversalRules = {};
		scope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { scope };
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.DataObject);
		searchParams.setQuery(query);
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		Long[] idTuple = { 1L, 2L };
		ArrayList<Long[]> idTuples = new ArrayList<>();
		idTuples.add(idTuple);
		Map<String, Long> idDictionary1 = new HashMap<String, Long>();
		idDictionary1.put(Constants.COLLECTION_IN_QUERY, 1L);
		idDictionary1.put(Constants.DATAOBJECT_IN_QUERY, 2L);
		List<Map<String, Long>> idDictionaries = new ArrayList<Map<String, Long>>();
		idDictionaries.add(idDictionary1);
		String selectionQuery = Neo4jEmitter.emitDataObjectSelectionQuery(query, userName);
		when(searchDAO.buildQueryAndGetIdDictionaryFromQuery(selectionQuery, coldovariables))
				.thenReturn(idDictionaries);
		ResultTriple resultTriple = new ResultTriple(1L, 2L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody(resultTriples, searchParams);
		assertEquals(dataObjectSearcher.search(searchBody, userName), responseBody);
	}

	@Test
	public void dataObjectIdNullTest() {
		SearchBody searchBody = new SearchBody();
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(null);
		TraversalRules[] traversalRules = {};
		scope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { scope };
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.DataObject);
		searchParams.setQuery(query);
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		Long[] idTuple = { 2L };
		ArrayList<Long[]> idTuples = new ArrayList<>();
		idTuples.add(idTuple);
		Map<String, Long> idDictionary1 = new HashMap<String, Long>();
		idDictionary1.put(Constants.DATAOBJECT_IN_QUERY, 2L);
		List<Map<String, Long>> idDictionaries = new ArrayList<Map<String, Long>>();
		idDictionaries.add(idDictionary1);
		String selectionQuery = Neo4jEmitter.emitCollectionDataObjectSelectionQuery(scope.getCollectionId(), query,
				userName);
		when(searchDAO.buildQueryAndGetIdDictionaryFromQuery(selectionQuery, dovariables)).thenReturn(idDictionaries);
		ResultTriple resultTriple = new ResultTriple(1L, 2L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody(resultTriples, searchParams);
		assertEquals(dataObjectSearcher.search(searchBody, userName), responseBody);
	}

	@Test
	public void nonEmptyTraversalRules() {
		SearchBody searchBody = new SearchBody();
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		TraversalRules[] traversalRules = { TraversalRules.children };
		scope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { scope };
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.DataObject);
		searchParams.setQuery(query);
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		Long[] idTuple = { 2L };
		ArrayList<Long[]> idTuples = new ArrayList<>();
		idTuples.add(idTuple);
		Map<String, Long> idDictionary1 = new HashMap<String, Long>();
		idDictionary1.put(Constants.DATAOBJECT_IN_QUERY, 2L);
		List<Map<String, Long>> idDictionaries = new ArrayList<Map<String, Long>>();
		idDictionaries.add(idDictionary1);
		String selectionQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(scope,
				scope.getTraversalRules()[0], query, userName);
		when(searchDAO.buildQueryAndGetIdDictionaryFromQuery(selectionQuery, dovariables)).thenReturn(idDictionaries);
		ResultTriple resultTriple = new ResultTriple(1L, 2L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody(resultTriples, searchParams);
		assertEquals(dataObjectSearcher.search(searchBody, userName), responseBody);
	}

	@Test
	public void emptyTraversalRules() {
		SearchBody searchBody = new SearchBody();
		SearchScope scope = new SearchScope();
		scope.setCollectionId(1L);
		scope.setDataObjectId(2L);
		TraversalRules[] traversalRules = {};
		scope.setTraversalRules(traversalRules);
		SearchScope[] scopes = { scope };
		SearchParams searchParams = new SearchParams();
		searchParams.setQueryType(QueryType.DataObject);
		searchParams.setQuery(query);
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		Long[] idTuple = { 2L };
		ArrayList<Long[]> idTuples = new ArrayList<>();
		idTuples.add(idTuple);
		Map<String, Long> idDictionary1 = new HashMap<String, Long>();
		idDictionary1.put(Constants.DATAOBJECT_IN_QUERY, 2L);
		List<Map<String, Long>> idDictionaries = new ArrayList<Map<String, Long>>();
		idDictionaries.add(idDictionary1);
		String selectionQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectSelectionQuery(scope, query, userName);
		when(searchDAO.buildQueryAndGetIdDictionaryFromQuery(selectionQuery, dovariables)).thenReturn(idDictionaries);
		ResultTriple resultTriple = new ResultTriple(1L, 2L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody(resultTriples, searchParams);
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchParams);
		assertEquals(dataObjectSearcher.search(searchBody, userName), responseBody);
	}

}
