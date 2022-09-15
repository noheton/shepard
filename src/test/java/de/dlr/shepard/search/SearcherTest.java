package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.util.TraversalRules;

public class SearcherTest extends BaseTestCase {

	@Mock
	StructuredDataSearcher structuredDataSearcher;

	@Mock
	CollectionSearcher collectionSearcher;

	@Mock
	DataObjectSearcher dataObjectSearcher;

	@Mock
	ReferenceSearcher referenceSearcher;

	@InjectMocks
	private Searcher searcher;

	@Test
	public void structuredDataSearchTest() {
		String userName = "user1";
		Long collectionId = 1L;
		Long dataObjectId = 2L;
		TraversalRules[] traversalRules = { TraversalRules.children, TraversalRules.parents };
		SearchScope scope = new SearchScope();
		scope.setCollectionId(collectionId);
		scope.setDataObjectId(dataObjectId);
		scope.setTraversalRules(traversalRules);
		SearchScope scopes[] = { scope };
		String query = "abc";
		QueryType queryType = QueryType.StructuredData;
		SearchParams searchParams = new SearchParams();
		searchParams.setQuery(query);
		searchParams.setQueryType(queryType);
		SearchBody searchBody = new SearchBody();
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		long referenceId = 3;
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(collectionId);
		resultTriple.setDataObjectId(dataObjectId);
		resultTriple.setReferenceId(referenceId);
		ResultTriple[] resultSet = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultSet);
		responseBody.setSearchParams(searchParams);
		ResponseBody myResponseBody = new ResponseBody();
		myResponseBody.setResultSet(resultSet);
		myResponseBody.setSearchParams(searchParams);
		when(structuredDataSearcher.search(searchBody, userName)).thenReturn(myResponseBody);
		ResponseBody response = searcher.search(searchBody, userName);
		assertEquals(myResponseBody, response);
	}

	@Test
	public void collectionSearchTest() {
		String userName = "user1";
		SearchScope scope = new SearchScope();
		SearchScope scopes[] = { scope };
		String query = "abc";
		QueryType queryType = QueryType.Collection;
		SearchParams searchParams = new SearchParams();
		searchParams.setQuery(query);
		searchParams.setQueryType(queryType);
		SearchBody searchBody = new SearchBody();
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(1L);
		ResultTriple[] resultSet = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultSet);
		responseBody.setSearchParams(searchParams);
		ResponseBody myResponseBody = new ResponseBody();
		myResponseBody.setResultSet(resultSet);
		myResponseBody.setSearchParams(searchParams);
		when(collectionSearcher.search(searchBody, userName)).thenReturn(myResponseBody);
		ResponseBody response = searcher.search(searchBody, userName);
		assertEquals(myResponseBody, response);
	}

	@Test
	public void dataObjectSearchTest() {
		String userName = "user1";
		Long collectionId = 1L;
		Long dataObjectId = 2L;
		TraversalRules[] traversalRules = { TraversalRules.children, TraversalRules.parents };
		SearchScope scope = new SearchScope();
		scope.setCollectionId(collectionId);
		scope.setDataObjectId(dataObjectId);
		scope.setTraversalRules(traversalRules);
		SearchScope scopes[] = { scope };
		String query = "abc";
		QueryType queryType = QueryType.DataObject;
		SearchParams searchParams = new SearchParams();
		searchParams.setQuery(query);
		searchParams.setQueryType(queryType);
		SearchBody searchBody = new SearchBody();
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		long referenceId = 3;
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(collectionId);
		resultTriple.setDataObjectId(dataObjectId);
		resultTriple.setReferenceId(referenceId);
		ResultTriple[] resultSet = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultSet);
		responseBody.setSearchParams(searchParams);
		ResponseBody myResponseBody = new ResponseBody();
		myResponseBody.setResultSet(resultSet);
		myResponseBody.setSearchParams(searchParams);
		when(dataObjectSearcher.search(searchBody, userName)).thenReturn(myResponseBody);
		ResponseBody response = searcher.search(searchBody, userName);
		assertEquals(myResponseBody, response);
	}

	@Test
	public void referenceSearchTest() {
		String userName = "user1";
		Long collectionId = 1L;
		Long dataObjectId = 2L;
		TraversalRules[] traversalRules = { TraversalRules.children, TraversalRules.parents };
		SearchScope scope = new SearchScope();
		scope.setCollectionId(collectionId);
		scope.setDataObjectId(dataObjectId);
		scope.setTraversalRules(traversalRules);
		SearchScope scopes[] = { scope };
		String query = "abc";
		QueryType queryType = QueryType.Reference;
		SearchParams searchParams = new SearchParams();
		searchParams.setQuery(query);
		searchParams.setQueryType(queryType);
		SearchBody searchBody = new SearchBody();
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		long referenceId = 3;
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(collectionId);
		resultTriple.setDataObjectId(dataObjectId);
		resultTriple.setReferenceId(referenceId);
		ResultTriple[] resultSet = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultSet);
		responseBody.setSearchParams(searchParams);
		ResponseBody myResponseBody = new ResponseBody();
		myResponseBody.setResultSet(resultSet);
		myResponseBody.setSearchParams(searchParams);
		when(referenceSearcher.search(searchBody, userName)).thenReturn(myResponseBody);
		ResponseBody response = searcher.search(searchBody, userName);
		assertEquals(myResponseBody, response);
	}

}
