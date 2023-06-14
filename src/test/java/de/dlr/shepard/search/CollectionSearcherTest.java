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

public class CollectionSearcherTest extends BaseTestCase {

	@Mock
	private SearchDAO searchDAO;

	@InjectMocks
	private CollectionSearcher collectionSearcher;

	private static final String[] colvariables = { Constants.COLLECTION_IN_QUERY };

	@Test
	public void test() {
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
		ArrayList<Long[]> collectionIdList = new ArrayList<>();
		collectionIdList.add(collectionIds);
		String selectionQuery = Neo4jEmitter.emitCollectionSelectionQuery(searchBody.getSearchParams().getQuery(),
				userName);
		Map<String, Long> idDictionary = new HashMap<String, Long>();
		idDictionary.put(Constants.COLLECTION_IN_QUERY, collectionId);
		List<Map<String, Long>> idDictionaries = new ArrayList<Map<String, Long>>();
		idDictionaries.add(idDictionary);
		when(searchDAO.buildQueryAndGetIdDictionaryFromQuery(selectionQuery, colvariables)).thenReturn(idDictionaries);
		ResultTriple resultTriple = new ResultTriple(collectionId);
		ResultTriple[] resultTriples = { resultTriple };
		ResponseBody responseBody = new ResponseBody(resultTriples, searchBody.getSearchParams());
		assertEquals(collectionSearcher.search(searchBody, userName), responseBody);
	}

}
