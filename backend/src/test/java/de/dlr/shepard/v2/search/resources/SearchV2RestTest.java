package de.dlr.shepard.v2.search.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.common.search.endpoints.BasicCollectionAttributes;
import de.dlr.shepard.common.search.io.QueryType;
import de.dlr.shepard.common.search.io.ResponseBody;
import de.dlr.shepard.common.search.io.ResultTriple;
import de.dlr.shepard.common.search.io.SearchParams;
import de.dlr.shepard.common.search.services.CollectionSearchService;
import de.dlr.shepard.common.search.services.DataObjectSearchService;
import de.dlr.shepard.common.search.services.PaginatedCollectionList;
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
import java.util.Optional;
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

    PaginatedCollectionList page = new PaginatedCollectionList(
      List.of(col),
      1,
      "LUMEN",
      Optional.of(0),
      Optional.of(50),
      BasicCollectionAttributes.createdAt,
      true
    );
    when(collectionSearchService.search(eq("LUMEN"), any(), any(), any(), anyBoolean())).thenReturn(page);
    stubEmptyDataObjects("LUMEN");

    Response resp = resource.search("LUMEN", 0, 50, 0, 50);

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
    stubEmptyCollections("TR-004");

    BasicEntityIO doIO = new BasicEntityIO();
    doIO.setAppId(DO_APP_ID);
    doIO.setName("TR-004");
    ResponseBody doResponse = new ResponseBody(
      new ResultTriple[] { new ResultTriple(COLL_NEO4J_ID, 2L) },
      new BasicEntityIO[] { doIO },
      new SearchParams("TR-004", QueryType.DataObject)
    );
    when(dataObjectSearchService.search(any())).thenReturn(doResponse);

    Collection owningCollection = new Collection(COLL_NEO4J_ID);
    owningCollection.setAppId(COLL_APP_ID);
    when(collectionDAO.findLightByNeo4jId(COLL_NEO4J_ID)).thenReturn(owningCollection);

    Response resp = resource.search("TR-004", 0, 50, 0, 50);

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
    Response resp = resource.search("  ", 0, 50, 0, 50);
    assertEquals(400, resp.getStatus());
  }

  @Test
  void nullQueryReturnsBadRequest() {
    Response resp = resource.search(null, 0, 50, 0, 50);
    assertEquals(400, resp.getStatus());
  }

  /** APISIMP-SEARCH-BAD-REQUEST-PLAIN-STRING — 400 must use problem+json, not plain text. */
  @Test
  void badRequestReturnsProblemJson() {
    Response resp = resource.search(null, 0, 50, 0, 50);
    assertEquals(400, resp.getStatus());
    assertEquals("application/problem+json",
        resp.getMediaType().toString(),
        "400 must be application/problem+json, not text/plain");
  }

  @Test
  void blankQueryAlsoReturnsProblemJson() {
    Response resp = resource.search("   ", 0, 50, 0, 50);
    assertEquals(400, resp.getStatus());
    assertEquals("application/problem+json", resp.getMediaType().toString());
  }

  @Test
  void pageSizeIsCappedAt200() {
    PaginatedCollectionList page = new PaginatedCollectionList(
      List.of(),
      0,
      "x",
      Optional.of(0),
      Optional.of(200),
      BasicCollectionAttributes.createdAt,
      true
    );
    when(collectionSearchService.search(eq("x"), any(), eq(Optional.of(200)), any(), anyBoolean())).thenReturn(page);
    stubEmptyDataObjects("x");

    Response resp = resource.search("x", 0, 9999, 0, 50);
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
    assertTrue(longFields.stream().allMatch(n -> n.equals("total") || n.equals("doTotal")), "Unexpected long field(s) in SearchV2ResultIO: " + longFields);
  }

  /** Regression: successful search response must carry X-Total-Count header matching body total. */
  @Test
  void responseIncludesXTotalCountHeader() {
    Collection col = new Collection(1L);
    col.setAppId(COLL_APP_ID);
    col.setName("LUMEN Campaign");
    PaginatedCollectionList page = new PaginatedCollectionList(
      List.of(col),
      3,
      "LUMEN",
      Optional.of(0),
      Optional.of(50),
      BasicCollectionAttributes.createdAt,
      true
    );
    when(collectionSearchService.search(eq("LUMEN"), any(), any(), any(), anyBoolean())).thenReturn(page);
    stubEmptyDataObjects("LUMEN");

    Response resp = resource.search("LUMEN", 0, 50, 0, 50);

    assertEquals(200, resp.getStatus());
    Object header = resp.getHeaders().getFirst("X-Total-Count");
    assertEquals(3L, header, "X-Total-Count header must match body total");
  }

  /** Regression: pageSize @PathParam on search() must carry @Min(1) and @Max(200). */
  @Test
  void pageSizeAnnotationHasMinOneAndMax200() throws NoSuchMethodException {
    Method searchMethod = SearchV2Rest.class.getMethod("search", String.class, int.class, int.class, int.class, int.class);
    Parameter pageSizeParam = searchMethod.getParameters()[2];
    List<Class<? extends Annotation>> annotationTypes = Arrays.stream(pageSizeParam.getAnnotations())
      .map(Annotation::annotationType)
      .collect(Collectors.toList());
    assertTrue(annotationTypes.contains(Min.class), "pageSize must have @Min");
    assertTrue(annotationTypes.contains(Max.class), "pageSize must have @Max");
    assertEquals(1L, pageSizeParam.getAnnotation(Min.class).value(), "@Min value must be 1");
    assertEquals(200L, pageSizeParam.getAnnotation(Max.class).value(), "@Max value must be 200");
  }

  // --- helpers ---

  private void stubEmptyCollections(String query) {
    PaginatedCollectionList empty = new PaginatedCollectionList(
      List.of(),
      0,
      query,
      Optional.of(0),
      Optional.of(50),
      BasicCollectionAttributes.createdAt,
      true
    );
    when(collectionSearchService.search(eq(query), any(), any(), any(), anyBoolean())).thenReturn(empty);
  }

  private void stubEmptyDataObjects(String query) {
    ResponseBody empty = new ResponseBody(
      new ResultTriple[0],
      new BasicEntityIO[0],
      new SearchParams(query, QueryType.DataObject)
    );
    when(dataObjectSearchService.search(any())).thenReturn(empty);
  }
}
