package de.dlr.shepard.search.unified;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.entities.Collection;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.StructuredDataReference;
import de.dlr.shepard.neo4Core.io.BasicEntityIO;
import de.dlr.shepard.search.unified.CollectionSearcher;
import de.dlr.shepard.search.unified.DataObjectSearcher;
import de.dlr.shepard.search.unified.QueryType;
import de.dlr.shepard.search.unified.ReferenceSearcher;
import de.dlr.shepard.search.unified.ResponseBody;
import de.dlr.shepard.search.unified.ResultTriple;
import de.dlr.shepard.search.unified.SearchBody;
import de.dlr.shepard.search.unified.SearchParams;
import de.dlr.shepard.search.unified.SearchScope;
import de.dlr.shepard.search.unified.Searcher;
import de.dlr.shepard.search.unified.StructuredDataSearcher;

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
	public void invalidQueryTest() {
		String userName = "user1";
		SearchScope[] scopes = { new SearchScope() };
		var params = new SearchParams("match invalid", QueryType.Collection);
		var searchBody = new SearchBody(scopes, params);
		assertThrows(InvalidBodyException.class, () -> searcher.search(searchBody, userName));
		verify(collectionSearcher, never()).search(searchBody, userName);
	}

	@Test
	public void collectionSearchTest() {
		String userName = "user1";
		SearchScope[] scopes = { new SearchScope() };
		var params = new SearchParams("{}", QueryType.Collection);
		var searchBody = new SearchBody(scopes, params);
		ResultTriple[] resultTriples = { new ResultTriple(1L) };
		BasicEntityIO[] results = { new BasicEntityIO(new Collection(1L)) };
		var expected = new ResponseBody(resultTriples, results, params);
		when(collectionSearcher.search(searchBody, userName)).thenReturn(expected);
		ResponseBody actual = searcher.search(searchBody, userName);
		assertEquals(expected, actual);
	}

	@Test
	public void dataObjectSearchTest() {
		String userName = "user1";
		SearchScope[] scopes = { new SearchScope() };
		var params = new SearchParams("{}", QueryType.DataObject);
		var searchBody = new SearchBody(scopes, params);
		ResultTriple[] resultTriples = { new ResultTriple(1L) };
		BasicEntityIO[] results = { new BasicEntityIO(new DataObject(1L)) };
		var expected = new ResponseBody(resultTriples, results, params);
		when(dataObjectSearcher.search(searchBody, userName)).thenReturn(expected);
		ResponseBody actual = searcher.search(searchBody, userName);
		assertEquals(expected, actual);
	}

	@Test
	public void referenceSearchTest() {
		String userName = "user1";
		SearchScope[] scopes = { new SearchScope() };
		var params = new SearchParams("{}", QueryType.Reference);
		var searchBody = new SearchBody(scopes, params);
		ResultTriple[] resultTriples = { new ResultTriple(1L) };
		BasicEntityIO[] results = { new BasicEntityIO(new BasicReference(1L)) };
		var expected = new ResponseBody(resultTriples, results, params);
		when(referenceSearcher.search(searchBody, userName)).thenReturn(expected);
		ResponseBody actual = searcher.search(searchBody, userName);
		assertEquals(expected, actual);
	}

	@Test
	public void structuredDataSearchTest() {
		String userName = "user1";
		SearchScope[] scopes = { new SearchScope() };
		var params = new SearchParams("{}", QueryType.StructuredData);
		var searchBody = new SearchBody(scopes, params);
		ResultTriple[] resultTriples = { new ResultTriple(1L) };
		BasicEntityIO[] results = { new BasicEntityIO(new StructuredDataReference(1L)) };
		var expected = new ResponseBody(resultTriples, results, params);
		when(structuredDataSearcher.search(searchBody, userName)).thenReturn(expected);
		ResponseBody actual = searcher.search(searchBody, userName);
		assertEquals(expected, actual);
	}

}
