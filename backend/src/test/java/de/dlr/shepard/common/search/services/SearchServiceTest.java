package de.dlr.shepard.common.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.search.endpoints.BasicCollectionAttributes;
import de.dlr.shepard.common.search.io.CollectionSearchParams;
import de.dlr.shepard.common.search.io.CollectionSearchResult;
import de.dlr.shepard.common.search.io.QueryType;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.ResultTriple;
import de.dlr.shepard.common.search.io.SearchBody;
import de.dlr.shepard.common.search.io.SearchParams;
import de.dlr.shepard.common.search.io.SearchScope;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class SearchServiceTest {

  @InjectMock
  StructuredDataSearchService structuredDataSearcher;

  @InjectMock
  CollectionSearchService collectionSearcher;

  @InjectMock
  DataObjectSearchService dataObjectSearcher;

  @InjectMock
  ReferenceSearchService referenceSearcher;

  @Inject
  SearchService searcher;

  @Test
  public void invalidQueryTest() {
    String userName = "user1";
    SearchScope[] scopes = { new SearchScope() };
    var params = new SearchParams("match invalid", QueryType.Collection);
    var searchBody = new SearchBody(scopes, params);
    assertThrows(InvalidBodyException.class, () -> searcher.search(searchBody, userName));
    verify(collectionSearcher, never()).search(
      searchBody.getSearchParams().getQuery(),
      userName,
      Optional.empty(),
      Optional.empty(),
      BasicCollectionAttributes.createdAt,
      false
    );
  }

  @Test
  public void collectionSearchTest() {
    String userName = "user1";
    SearchScope[] scopes = { new SearchScope() };
    var params = new SearchParams("{}", QueryType.Collection);
    var searchBody = new SearchBody(scopes, params);
    Collection collection = new Collection(1L);
    collection.setShepardId(collection.getId());
    List<CollectionIO> results = List.of(new CollectionIO(collection));
    var collectionSearchResultMock = new CollectionSearchResult(
      results,
      new CollectionSearchParams(params.getQuery()),
      0
    );

    List<Collection> mockCollectionList = new ArrayList<Collection>();
    mockCollectionList.add(collection);

    PaginatedCollectionList paginatedCollectionListMock = new PaginatedCollectionList(
      mockCollectionList,
      1,
      params.getQuery(),
      Optional.empty(),
      Optional.empty(),
      BasicCollectionAttributes.createdAt,
      true
    );

    when(collectionSearcher.search(anyString(), anyString(), any(), any(), any(), any())).thenReturn(
      paginatedCollectionListMock
    );
    ResponseBody actual = searcher.search(searchBody, userName);
    assertEquals(collectionSearchResultMock.toResponseBody(), actual);
  }

  @Test
  public void dataObjectSearchTest() {
    String userName = "user1";
    SearchScope[] scopes = { new SearchScope() };
    var params = new SearchParams("{}", QueryType.DataObject);
    var searchBody = new SearchBody(scopes, params);
    ResultTriple[] resultTriples = { new ResultTriple(1L) };
    DataObject dataObject = new DataObject(1L);
    dataObject.setShepardId(dataObject.getId());
    BasicEntityIO[] results = { new BasicEntityIO(dataObject) };
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
    BasicReference reference = new BasicReference(1L);
    reference.setShepardId(reference.getId());
    BasicEntityIO[] results = { new BasicEntityIO(reference) };
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
    StructuredDataReference reference = new StructuredDataReference(1L);
    reference.setShepardId(reference.getId());
    BasicEntityIO[] results = { new BasicEntityIO(reference) };
    var expected = new ResponseBody(resultTriples, results, params);
    when(structuredDataSearcher.search(searchBody, userName)).thenReturn(expected);
    ResponseBody actual = searcher.search(searchBody, userName);
    assertEquals(expected, actual);
  }
}
