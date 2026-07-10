package de.dlr.shepard.v2.watches.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.watches.entities.Watch;
import de.dlr.shepard.v2.watches.io.WatchIO;
import de.dlr.shepard.v2.watches.services.WatchService;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-COLLECTION-WATCHES-IN-MEMORY-PAGING — unit tests for bounded
 * pagination on {@link CollectionWatchesRest#list}.
 */
class CollectionWatchesRestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-b000-000000000001";
  static final String W1_APP_ID   = "018f9c5a-7e26-7000-b000-000000000002";
  static final String W2_APP_ID   = "018f9c5a-7e26-7000-b000-000000000003";

  @Mock
  WatchService service;

  CollectionWatchesRest resource;

  static WatchIO watch(String appId) {
    return new WatchIO(appId, Watch.Kind.TIMESERIES,
      "018f9c5a-0000-0000-0000-000000000099",
      "Live sensors", "available", 1L, "test");
  }

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionWatchesRest();
    resource.service = service;
  }

  @Test
  void list_callsCountThenBoundedList() {
    when(service.count(COLL_APP_ID)).thenReturn(2L);
    when(service.list(COLL_APP_ID, 0, 50)).thenReturn(List.of(watch(W1_APP_ID), watch(W2_APP_ID)));

    Response r = resource.list(COLL_APP_ID, 0, 50);

    assertEquals(200, r.getStatus());
    verify(service).count(COLL_APP_ID);
    verify(service).list(COLL_APP_ID, 0, 50);
    verify(service, never()).list(anyString());
  }

  @Test
  void list_page0_returnsItemsAndXTotalCount() {
    when(service.count(COLL_APP_ID)).thenReturn(2L);
    when(service.list(COLL_APP_ID, 0, 50)).thenReturn(List.of(watch(W1_APP_ID), watch(W2_APP_ID)));

    Response r = resource.list(COLL_APP_ID, 0, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<WatchIO> body = (PagedResponseIO<WatchIO>) r.getEntity();
    assertEquals(2, body.items().size());
    assertEquals("2", r.getHeaderString("X-Total-Count"));
  }

  @Test
  void list_page1_computesCorrectSkip() {
    when(service.count(COLL_APP_ID)).thenReturn(10L);
    when(service.list(eq(COLL_APP_ID), eq(3), eq(3)))
      .thenReturn(List.of(watch(W1_APP_ID)));

    Response r = resource.list(COLL_APP_ID, 1, 3);

    assertEquals(200, r.getStatus());
    // page=1, pageSize=3 → skip=3
    verify(service).list(COLL_APP_ID, 3, 3);
    assertEquals("10", r.getHeaderString("X-Total-Count"));
  }

  @Test
  void list_xTotalCountReflectsCountNotItemsSize() {
    when(service.count(COLL_APP_ID)).thenReturn(100L);
    when(service.list(COLL_APP_ID, 0, 50)).thenReturn(List.of(watch(W1_APP_ID)));

    Response r = resource.list(COLL_APP_ID, 0, 50);

    assertEquals("100", r.getHeaderString("X-Total-Count"));
    @SuppressWarnings("unchecked")
    PagedResponseIO<WatchIO> body = (PagedResponseIO<WatchIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(100L, body.total());
  }

  @Test
  void list_emptyPage_returnsEmptyItemsButCorrectTotal() {
    when(service.count(COLL_APP_ID)).thenReturn(2L);
    when(service.list(COLL_APP_ID, 990, 50)).thenReturn(List.of());

    Response r = resource.list(COLL_APP_ID, 99, 10);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<WatchIO> body = (PagedResponseIO<WatchIO>) r.getEntity();
    assertTrue(body.items().isEmpty());
    assertEquals("2", r.getHeaderString("X-Total-Count"));
  }

  @Test
  void list_page_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method method = CollectionWatchesRest.class.getMethod(
        "list", String.class, int.class, int.class);
    var param = java.util.Arrays.stream(method.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && "page".equals(qp.value());
        })
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(param, "page must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    org.junit.jupiter.api.Assertions.assertNotNull(ann, "page must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank());
  }

  @Test
  void list_pageSize_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Method method = CollectionWatchesRest.class.getMethod(
        "list", String.class, int.class, int.class);
    var param = java.util.Arrays.stream(method.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && "pageSize".equals(qp.value());
        })
        .findFirst().orElse(null);
    org.junit.jupiter.api.Assertions.assertNotNull(param, "pageSize must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    org.junit.jupiter.api.Assertions.assertNotNull(ann, "pageSize must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank());
  }

  @Test
  void classPath_usesAppId() {
    // APISIMP-COLLWATCHES-PATHPARAM regression: class-level @Path must use {appId}, not {collectionAppId}.
    var pathAnn = CollectionWatchesRest.class.getAnnotation(jakarta.ws.rs.Path.class);
    org.junit.jupiter.api.Assertions.assertNotNull(pathAnn, "CollectionWatchesRest must carry @Path");
    assertTrue(pathAnn.value().contains("{appId}"),
        "class-level @Path must contain {appId}, got: " + pathAnn.value());
    org.junit.jupiter.api.Assertions.assertFalse(pathAnn.value().contains("{collectionAppId}"),
        "class-level @Path must NOT contain {collectionAppId}, got: " + pathAnn.value());
  }
}
