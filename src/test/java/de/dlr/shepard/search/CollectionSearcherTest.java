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

public class CollectionSearcherTest extends BaseTestCase {

	@Mock
	private SearchDAO searchDAO;

	@InjectMocks
	private CollectionSearcher collectionSearcher;

	@Test
	public void test() {
		String userName = "user1";
		var collection = new Collection(1L);
		SearchScope scopes[] = { new SearchScope() };
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
		SearchParams searchParams = new SearchParams(query, queryType);
		SearchBody searchBody = new SearchBody(scopes, searchParams);
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
