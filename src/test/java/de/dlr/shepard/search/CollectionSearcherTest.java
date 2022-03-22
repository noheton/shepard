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

public class CollectionSearcherTest extends BaseTestCase {

	@Mock
	private SearchDAO searchDAO;

	@InjectMocks
	private CollectionSearcher collectionSearcher;

	private static final String[] colvariables = { "col" };

	@Test
	public void test() throws ShepardParserException {
		String userName = "user1";
		Long collectionId = 1L;
		TraversalRules[] traversalRules = {};
		SearchScope scope = new SearchScope();
		scope.setTraversalRules(traversalRules);
		SearchScope scopes[] = { scope };
		String query = String.format("""
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
				]}""", collectionId);
		QueryType queryType = QueryType.Collection;
		SearchParams searchParams = new SearchParams();
		searchParams.setQuery(query);
		searchParams.setQueryType(queryType);
		SearchBody searchBody = new SearchBody();
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		Long[] collectionIds = { collectionId };
		ArrayList<Long[]> collectionIdList = new ArrayList<Long[]>();
		collectionIdList.add(collectionIds);
		String searchQuery = Neo4jEmitter.emitCollectionQuery(searchBody.getSearchParams().getQuery(), userName);
		when(searchDAO.getIdsFromQuery(searchQuery, colvariables)).thenReturn(collectionIdList);
		ResultTriple resultTriple = new ResultTriple();
		resultTriple.setCollectionId(collectionId);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody();
		responseBody.setResultSet(resultTriples);
		responseBody.setSearchParams(searchBody.getSearchParams());
		assertEquals(collectionSearcher.search(searchBody, userName), responseBody);
	}

}
