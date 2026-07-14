package de.dlr.shepard.v2.search.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.search.endpoints.BasicCollectionAttributes;
import de.dlr.shepard.common.search.io.QueryType;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.ResultTriple;
import de.dlr.shepard.common.search.io.SearchParams;
import de.dlr.shepard.common.search.services.CollectionSearchService;
import de.dlr.shepard.common.search.services.DataObjectSearchService;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.v2.search.io.SearchV2ItemIO;
import de.dlr.shepard.v2.search.io.SearchV2ResultIO;
import jakarta.ws.rs.core.Response;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link SearchV2Rest}.
 *
 * <p>Mock-based; no Quarkus boot. Covers the happy paths and the numeric-id
 * regression guard: {@link SearchV2ItemIO} must expose only the stable
 * {@code appId}, never a Neo4j node id.
 */
class SearchV2RestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000010";
  static final String DO_APP_ID = "018f9c5a-7e26-7000-a000-000000000020";
  static final long COLL_NEO4J_ID = 42L;

  @Mock
  CollectionSearchService collectionSearchService;

  @Mock
  DataObjectSearchService dataObjectSearchService;

  @Mock
  CollectionDAO collectionDAO;

  SearchV2Rest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new SearchV2Rest();
    resource.collectionSearchService = collectionSearchService;
    resource.dataObjectSearchService = dataObjectSearchService;
    resource.collectionDAO = collectionDAO;
  }

  @Test
  void collectionsIncludeAppIdNotNumericId() {
    Collection col = new Collection(1L);
    col.setAppId(COLL_APP_ID);
    col.setName("LUMEN Campaign");

    when(collectionSearchService.count(eq("LUMEN"))).thenReturn(1);
    when(collectionSearchService.searchSlice(eq("LUMEN"), anyInt(), anyInt(), any(), anyBoolean()))
        .thenReturn(List.of(col));
    stubEmptyDataObjects();

    Response resp = resource.search("LUMEN", 0, 50, null);

    assertEquals(200, resp.getStatus());
    SearchV2ResultIO result = (SearchV2ResultIO) resp.getEntity();
    List<SearchV2ItemIO> colItems = result
      .getItems()
      .stream()
      .filter(i -> "collection".equals(i.getKind()))
      .collect(Collectors.toList());
    assertEquals(1, colItems.size());
    assertEquals(COLL_APP_ID, colItems.get(0).getAppId());
    assertEquals("LUMEN Campaign", colItems.get(0).getName());
    assertEquals(1L, result.getTotal());
    assertEquals("LUMEN", result.getQuery());
  }

  @Test
  void dataObjectsIncludeAppIdAndParentCollectionAppId() {
    stubEmptyCollections();

    BasicEntityIO doIO = new BasicEntityIO();
    doIO.setAppId(DO_APP_ID);
    doIO.setName("TR-004");
    ResponseBody doResponse = new ResponseBody(
      new ResultTriple[] { new ResultTriple(COLL_NEO4J_ID, 2L) },
      new BasicEntityIO[] { doIO },
      new SearchParams("TR-004", QueryType.DataObject)
    );
    when(dataObjectSearchService.count(any())).thenReturn(1);
    when(dataObjectSearchService.searchPaged(any(), anyInt(), anyInt())).thenReturn(doResponse);

    Collection owningCollection = new Collection(COLL_NEO4J_ID);
    owningCollection.setAppId(COLL_APP_ID);
    when(collectionDAO.findLightByNeo4jId(COLL_NEO4J_ID)).thenReturn(owningCollection);

    Response resp = resource.search("TR-004", 0, 50, null);

    assertEquals(200, resp.getStatus());
    SearchV2ResultIO result = (SearchV2ResultIO) resp.getEntity();
    List<SearchV2ItemIO> doItems = result
      .getItems()
      .stream()
      .filter(i -> "dataobject".equals(i.getKind()))
      .collect(Collectors.toList());
    assertEquals(1, doItems.size());
    assertEquals(DO_APP_ID, doItems.get(0).getAppId());
    assertEquals("TR-004", doItems.get(0).getName());
    assertEquals(COLL_APP_ID, doItems.get(0).getParentCollectionAppId());
  }

  @Test
  void blankQueryReturnsBadRequest() {
    Response resp = resource.search("  ", 0, 50, null);
    assertEquals(400, resp.getStatus());
  }

  @Test
  void nullQueryReturnsBadRequest() {
    Response resp = resource.search(null, 0, 50, null);
    assertEquals(400, resp.getStatus());
  }

  /** APISIMP-SEARCH-BAD-REQUEST-PLAIN-STRING — 400 must use problem+json, not plain text. */
  @Test
  void badRequestReturnsProblemJson() {
    Response resp = resource.search(null, 0, 50, null);
    assertEquals(400, resp.getStatus());
    assertEquals("application/problem+json",
        resp.getMediaType().toString(),
        "400 must be application/problem+json, not text/plain");
  }

  @Test
  void blankQueryAlsoReturnsProblemJson() {
    Response resp = resource.search("   ", 0, 50, null);
    assertEquals(400, resp.getStatus());
    assertEquals("application/problem+json", resp.getMediaType().toString());
  }

  @Test
  void pageSizeIsCappedAt200() {
    stubEmptyCollections();
    stubEmptyDataObjects();

    Response resp = resource.search("x", 0, 9999, null);
    assertEquals(200, resp.getStatus());
    SearchV2ResultIO result = (SearchV2ResultIO) resp.getEntity();
    assertEquals(200, result.getPageSize());
  }

  /** Regression: SearchV2ItemIO must not declare a numeric Neo4j id field. */
  @Test
  void searchV2ItemIoHasNoNumericIdField() {
    List<String> numericFields = Arrays.stream(SearchV2ItemIO.class.getDeclaredFields())
      .filter(f -> f.getType() == long.class || f.getType() == Long.class)
      .map(Field::getName)
      .collect(Collectors.toList());
    assertTrue(numericFields.isEmpty(), "SearchV2ItemIO must not expose numeric ids: " + numericFields);
  }

  /** Regression: SearchV2ResultIO must not expose a Neo4j id — only count fields. */
  @Test
  void searchV2ResultIoNumericFieldsAreCountsNotIds() {
    List<String> longFields = Arrays.stream(SearchV2ResultIO.class.getDeclaredFields())
      .filter(f -> f.getType() == long.class || f.getType() == Long.class)
      .map(Field::getName)
      .collect(Collectors.toList());
    assertTrue(longFields.stream().allMatch(n -> n.equals("total")),
        "Unexpected long field(s) in SearchV2ResultIO: " + longFields);
  }

  /** Regression: pageSize @PathParam on search() must carry @Min(1) and @Max(200). */
  @Test
  void pageSizeAnnotationHasMinOneAndMax200() throws NoSuchMethodException {
    Method searchMethod = SearchV2Rest.class.getMethod(
        "search", String.class, int.class, int.class, String.class);
    Parameter pageSizeParam = searchMethod.getParameters()[2];
    List<Class<? extends Annotation>> annotationTypes = Arrays.stream(pageSizeParam.getAnnotations())
      .map(Annotation::annotationType)
      .collect(Collectors.toList());
    assertTrue(annotationTypes.contains(Min.class), "pageSize must have @Min");
    assertTrue(annotationTypes.contains(Max.class), "pageSize must have @Max");
    assertEquals(1L, pageSizeParam.getAnnotation(Min.class).value(), "@Min value must be 1");
    assertEquals(200L, pageSizeParam.getAnnotation(Max.class).value(), "@Max value must be 200");
  }

  // --- SEARCH-V2-1: collectionAppId scoping tests ---

  @Test
  void unknownCollectionAppIdReturnsBadRequest() {
    when(collectionDAO.findByAppId("bad-appid")).thenReturn(null);

    Response resp = resource.search("TR-004", 0, 50, "bad-appid");

    assertEquals(400, resp.getStatus());
    assertEquals("application/problem+json", resp.getMediaType().toString());
  }

  @Test
  void scopedSearchSkipsCollectionResults() {
    Collection scopeCol = new Collection(COLL_NEO4J_ID);
    scopeCol.setAppId(COLL_APP_ID);
    when(collectionDAO.findByAppId(COLL_APP_ID)).thenReturn(scopeCol);
    stubEmptyDataObjects();

    Response resp = resource.search("TR-004", 0, 50, COLL_APP_ID);

    assertEquals(200, resp.getStatus());
    SearchV2ResultIO result = (SearchV2ResultIO) resp.getEntity();
    long collectionItems = result.getItems().stream()
        .filter(i -> "collection".equals(i.getKind())).count();
    assertEquals(0, collectionItems, "Collection items must be omitted when collectionAppId is scoped");
    // CollectionSearchService must not be called when scoped.
    verify(collectionSearchService, never()).count(any());
    verify(collectionSearchService, never()).searchSlice(any(), anyInt(), anyInt(), any(), anyBoolean());
  }

  @Test
  void scopedSearchPassesCollectionIdToDataObjectService() {
    Collection scopeCol = new Collection(COLL_NEO4J_ID);
    scopeCol.setAppId(COLL_APP_ID);
    when(collectionDAO.findByAppId(COLL_APP_ID)).thenReturn(scopeCol);

    BasicEntityIO doIO = new BasicEntityIO();
    doIO.setAppId(DO_APP_ID);
    doIO.setName("TR-004");
    ResponseBody doResponse = new ResponseBody(
      new ResultTriple[] { new ResultTriple(COLL_NEO4J_ID, 2L) },
      new BasicEntityIO[] { doIO },
      new SearchParams("TR-004", QueryType.DataObject)
    );
    when(dataObjectSearchService.count(any())).thenReturn(1);
    when(dataObjectSearchService.searchPaged(any(), anyInt(), anyInt())).thenReturn(doResponse);
    when(collectionDAO.findLightByNeo4jId(COLL_NEO4J_ID)).thenReturn(scopeCol);

    Response resp = resource.search("TR-004", 0, 50, COLL_APP_ID);

    assertEquals(200, resp.getStatus());
    SearchV2ResultIO result = (SearchV2ResultIO) resp.getEntity();
    List<SearchV2ItemIO> doItems = result.getItems().stream()
        .filter(i -> "dataobject".equals(i.getKind())).collect(Collectors.toList());
    assertEquals(1, doItems.size());
    assertEquals(DO_APP_ID, doItems.get(0).getAppId());
    // Verify DataObjectSearchService received a scope with the collection's Neo4j id.
    verify(dataObjectSearchService).count(argThat(body ->
        body.getScopes() != null &&
        body.getScopes().length == 1 &&
        Long.valueOf(COLL_NEO4J_ID).equals(body.getScopes()[0].getCollectionId())));
  }

  // --- APISIMP-SEARCH-IN-MEMORY-MERGE-PAGE: cap tests ---

  @Test
  void combinedResultSetIsCappedAt1000() {
    // 600 collections + 600 DOs = 1200; capped at 1000. Page 0 of 50 comes entirely from collections.
    when(collectionSearchService.count(eq("x"))).thenReturn(600);
    List<Collection> colSlice = new java.util.ArrayList<>();
    for (int i = 0; i < 50; i++) {
      Collection c = new Collection((long) i);
      c.setAppId("018f9c5a-0000-7000-a000-" + String.format("%012d", i));
      c.setName("Col-" + i);
      colSlice.add(c);
    }
    when(collectionSearchService.searchSlice(eq("x"), anyInt(), anyInt(), any(), anyBoolean()))
        .thenReturn(colSlice);
    when(dataObjectSearchService.count(any())).thenReturn(600);
    // DO slice not reached on page 0 (all 50 items fall within the collection range).

    Response resp = resource.search("x", 0, 50, null);

    assertEquals(200, resp.getStatus());
    SearchV2ResultIO result = (SearchV2ResultIO) resp.getEntity();
    assertEquals(SearchV2Rest.SEARCH_RESULT_CAP, result.getTotal(),
        "total must be capped at SEARCH_RESULT_CAP when combined exceeds it");
    assertEquals(50, result.getItems().size());
  }

  @Test
  void combinedResultSetBelowCapIsUncapped() {
    // 3 collections + 2 DOs = 5 total — no cap should apply.
    when(collectionSearchService.count(eq("y"))).thenReturn(3);
    List<Collection> colSlice = new java.util.ArrayList<>();
    for (int i = 0; i < 3; i++) {
      Collection c = new Collection((long) i);
      c.setAppId("018f9c5a-0000-7000-a000-" + String.format("%012d", i));
      c.setName("Col-" + i);
      colSlice.add(c);
    }
    when(collectionSearchService.searchSlice(eq("y"), anyInt(), anyInt(), any(), anyBoolean()))
        .thenReturn(colSlice);

    when(dataObjectSearchService.count(any())).thenReturn(2);
    BasicEntityIO[] dos = new BasicEntityIO[2];
    ResultTriple[] triples = new ResultTriple[2];
    for (int i = 0; i < 2; i++) {
      dos[i] = new BasicEntityIO();
      dos[i].setAppId("019f9c5a-0000-7000-a000-" + String.format("%012d", i));
      dos[i].setName("DO-" + i);
      triples[i] = new ResultTriple(null, (long) i);
    }
    when(dataObjectSearchService.searchPaged(any(), anyInt(), anyInt()))
        .thenReturn(new ResponseBody(triples, dos, new SearchParams("y", QueryType.DataObject)));

    Response resp = resource.search("y", 0, 50, null);

    assertEquals(200, resp.getStatus());
    SearchV2ResultIO result = (SearchV2ResultIO) resp.getEntity();
    assertEquals(5L, result.getTotal(), "total must be exact count when below cap");
    assertEquals(5, result.getItems().size());
  }

  // --- helpers ---

  private void stubEmptyCollections() {
    when(collectionSearchService.count(any())).thenReturn(0);
  }

  private void stubEmptyDataObjects() {
    when(dataObjectSearchService.count(any())).thenReturn(0);
  }
}
