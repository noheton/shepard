package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.ShepardParserException;
import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.util.TraversalRules;

public class DataObjectSearcherTest extends BaseTestCase {

	@Mock
	private SearchDAO searchDAO;

	@InjectMocks
	private DataObjectSearcher dataObjectSearcher;

	private static final String[] coldovariables = { "col", "do" };
	private static final String[] dovariables = { "do" };
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
	public void collectionIdNullDataObjectIdNullTest() throws ShepardParserException {
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
		ArrayList<Long[]> idTuples = new ArrayList<Long[]>();
		idTuples.add(idTuple);
		String searchQuery = Neo4jEmitter.emitDataObjectQuery(query, userName);
		when(searchDAO.getIdsFromQuery(searchQuery, coldovariables)).thenReturn(idTuples);
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(1L);
		resultTriple.setDataObjectId(2L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchParams);
		assertEquals(dataObjectSearcher.search(searchBody, userName), responseBody);
	}

	@Test
	public void dataObjectIdNullTest() throws ShepardParserException {
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
		ArrayList<Long[]> idTuples = new ArrayList<Long[]>();
		idTuples.add(idTuple);
		String searchQuery = Neo4jEmitter.emitCollectionDataObjectQuery(scope.getCollectionId(), query, userName);
		when(searchDAO.getIdsFromQuery(searchQuery, dovariables)).thenReturn(idTuples);
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(1L);
		resultTriple.setDataObjectId(2L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchParams);
		assertEquals(dataObjectSearcher.search(searchBody, userName), responseBody);
	}

	@Test
	public void nonEmptyTraversalRules() throws ShepardParserException {
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
		ArrayList<Long[]> idTuples = new ArrayList<Long[]>();
		idTuples.add(idTuple);
		String searchQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectQuery(scope, scope.getTraversalRules()[0],
				query, userName);
		when(searchDAO.getIdsFromQuery(searchQuery, dovariables)).thenReturn(idTuples);
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(1L);
		resultTriple.setDataObjectId(2L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchParams);
		assertEquals(dataObjectSearcher.search(searchBody, userName), responseBody);
	}

	@Test
	public void emptyTraversalRules() throws ShepardParserException {
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
		ArrayList<Long[]> idTuples = new ArrayList<Long[]>();
		idTuples.add(idTuple);
		String searchQuery = Neo4jEmitter.emitCollectionDataObjectDataObjectQuery(scope, query, userName);
		when(searchDAO.getIdsFromQuery(searchQuery, dovariables)).thenReturn(idTuples);
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(1L);
		resultTriple.setDataObjectId(2L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchParams);
		assertEquals(dataObjectSearcher.search(searchBody, userName), responseBody);
	}

}
