package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.dao.SearchDAO;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.TraversalRules;

public class CollectionSearcherTest extends BaseTestCase {

	@Mock
	private SearchDAO searchDAO;

	@InjectMocks
	private CollectionSearcher collectionSearcher;

	@Test
	public void test() {
		String userName = "user1";
		var collection = new Collection(1L);
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
				]}""", 1L);
		QueryType queryType = QueryType.Collection;
		SearchParams searchParams = new SearchParams();
		searchParams.setQuery(query);
		searchParams.setQueryType(queryType);
		SearchBody searchBody = new SearchBody();
		searchBody.setScopes(scopes);
		searchBody.setSearchParams(searchParams);
		String selectionQuery = Neo4jEmitter.emitCollectionSelectionQuery(searchBody.getSearchParams().getQuery(),
				userName);
		when(searchDAO.findCollections(selectionQuery, Constants.COLLECTION_IN_QUERY)).thenReturn(List.of(collection));
		ResultTriple[] resultTriples = { new ResultTriple(collection.getId()) };
		BasicEntityIO[] results = { new BasicEntityIO(collection) };
		ResponseBody responseBody = new ResponseBody(resultTriples, results, searchBody.getSearchParams());
		var actual = collectionSearcher.search(searchBody, userName);
		assertEquals(responseBody, actual);
	}

}
