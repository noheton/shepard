package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.util.TraversalRules;

public class ReferenceSearcherTest extends BaseTestCase {

	@Mock
	private SearchDAO searchDAO;

	@InjectMocks
	private ReferenceSearcher referenceSearcher;

	private static final String[] coldobrvariables = { "col", "do", "br" };
	private static final String[] dobrvariables = { "do", "br" };

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
		Long[] idTuple = { 1L, 2L, 3L };
		ArrayList<Long[]> idTuples = new ArrayList<>();
		idTuples.add(idTuple);
		String searchQuery = Neo4jEmitter.emitBasicReferenceQuery(query, userName);
		when(searchDAO.getIdsFromQuery(searchQuery, coldobrvariables)).thenReturn(idTuples);
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(1L);
		resultTriple.setDataObjectId(2L);
		resultTriple.setReferenceId(3L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchParams);
		assertEquals(referenceSearcher.search(searchBody, userName), responseBody);
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
		Long[] idTuple = { 2L, 3L };
		ArrayList<Long[]> idTuples = new ArrayList<>();
		idTuples.add(idTuple);
		String searchQuery = Neo4jEmitter.emitCollectionBasicReferenceQuery(query, scope.getCollectionId(), userName);
		when(searchDAO.getIdsFromQuery(searchQuery, dobrvariables)).thenReturn(idTuples);
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(1L);
		resultTriple.setDataObjectId(2L);
		resultTriple.setReferenceId(3L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchParams);
		assertEquals(referenceSearcher.search(searchBody, userName), responseBody);
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
		Long[] idTuple = { 2L, 3L };
		ArrayList<Long[]> idTuples = new ArrayList<>();
		idTuples.add(idTuple);
		String searchQuery = Neo4jEmitter.emitCollectionDataObjectBasicReferenceQuery(scope,
				scope.getTraversalRules()[0], query, userName);
		when(searchDAO.getIdsFromQuery(searchQuery, dobrvariables)).thenReturn(idTuples);
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(1L);
		resultTriple.setDataObjectId(2L);
		resultTriple.setReferenceId(3L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchParams);
		assertEquals(referenceSearcher.search(searchBody, userName), responseBody);
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
		Long[] idTuple = { 2L, 3L };
		ArrayList<Long[]> idTuples = new ArrayList<>();
		idTuples.add(idTuple);
		String searchQuery = Neo4jEmitter.emitCollectionDataObjectReferenceQuery(scope, query, userName);
		when(searchDAO.getIdsFromQuery(searchQuery, dobrvariables)).thenReturn(idTuples);
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(1L);
		resultTriple.setDataObjectId(2L);
		resultTriple.setReferenceId(3L);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchParams);
		assertEquals(referenceSearcher.search(searchBody, userName), responseBody);
	}

}
