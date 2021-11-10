package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.util.TraversalRules;

public class SearcherTest extends BaseTestCase {

	@Mock
	StructuredDataSearcher structuredDataSearcher;

	@Mock
	private Session session;

	@InjectMocks
	private Searcher searcher;

	@Test
	public void searchTest() {
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
		when(structuredDataSearcher.search(searchBody)).thenReturn(myResponseBody);
		ResponseBody response = searcher.search(searchBody);
		assertEquals(response, myResponseBody);
	}

}
