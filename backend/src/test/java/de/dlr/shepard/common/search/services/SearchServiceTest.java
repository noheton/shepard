package de.dlr.shepard.common.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
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

  @InjectMock
  UserService userService;

  private final User user = new User("Testuser");

  @Test
  public void invalidQueryTest() {
    SearchScope[] scopes = { new SearchScope() };
    var params = new SearchParams("match invalid", QueryType.Collection);
    var searchBody = new SearchBody(scopes, params);

    when(userService.getCurrentUser()).thenReturn(user);

    assertThrows(InvalidBodyException.class, () -> searcher.search(searchBody));
    verify(collectionSearcher, never()).search(
      searchBody.getSearchParams().getQuery(),
      Optional.empty(),
      Optional.empty(),
      BasicCollectionAttributes.createdAt,
      false
    );
  }

  @Test
  public void collectionSearchTest() {
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

    when(userService.getCurrentUser()).thenReturn(user);
    when(collectionSearcher.search(anyString(), any(), any(), any(), any())).thenReturn(paginatedCollectionListMock);
    ResponseBody actual = searcher.search(searchBody);
    assertEquals(collectionSearchResultMock.toResponseBody(), actual);
  }

  @Test
  public void dataObjectSearchTest() {
    SearchScope[] scopes = { new SearchScope() };
    var params = new SearchParams("{}", QueryType.DataObject);
    var searchBody = new SearchBody(scopes, params);
    ResultTriple[] resultTriples = { new ResultTriple(1L) };
    DataObject dataObject = new DataObject(1L);
    dataObject.setShepardId(dataObject.getId());
    BasicEntityIO[] results = { new BasicEntityIO(dataObject) };
    var expected = new ResponseBody(resultTriples, results, params);

    when(userService.getCurrentUser()).thenReturn(user);
    when(dataObjectSearcher.search(searchBody)).thenReturn(expected);

    ResponseBody actual = searcher.search(searchBody);
    assertEquals(expected, actual);
  }

  @Test
  public void referenceSearchTest() {
    SearchScope[] scopes = { new SearchScope() };
    var params = new SearchParams("{}", QueryType.Reference);
    var searchBody = new SearchBody(scopes, params);
    ResultTriple[] resultTriples = { new ResultTriple(1L) };
    BasicReference reference = new BasicReference(1L);
    reference.setShepardId(reference.getId());
    BasicEntityIO[] results = { new BasicEntityIO(reference) };
    var expected = new ResponseBody(resultTriples, results, params);

    when(referenceSearcher.search(searchBody)).thenReturn(expected);
    when(userService.getCurrentUser()).thenReturn(user);

    ResponseBody actual = searcher.search(searchBody);
    assertEquals(expected, actual);
  }

  @Test
  public void structuredDataSearchTest() {
    SearchScope[] scopes = { new SearchScope() };
    var params = new SearchParams("{}", QueryType.StructuredData);
    var searchBody = new SearchBody(scopes, params);
    ResultTriple[] resultTriples = { new ResultTriple(1L) };
    StructuredDataReference reference = new StructuredDataReference(1L);
    reference.setShepardId(reference.getId());
    BasicEntityIO[] results = { new BasicEntityIO(reference) };
    var expected = new ResponseBody(resultTriples, results, params);

    when(structuredDataSearcher.search(searchBody)).thenReturn(expected);
    when(userService.getCurrentUser()).thenReturn(user);

    ResponseBody actual = searcher.search(searchBody);
    assertEquals(expected, actual);
  }
}
