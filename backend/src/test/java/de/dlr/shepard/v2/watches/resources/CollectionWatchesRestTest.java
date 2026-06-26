package de.dlr.shepard.v2.watches.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * APISIMP-WATCHES-LIST-NO-PAGINATION — unit tests for pagination on
 * {@link CollectionWatchesRest#list}.
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
  void list_noPagination_returnsAllAndXTotalCount() {
    when(service.list(COLL_APP_ID))
      .thenReturn(List.of(watch(W1_APP_ID), watch(W2_APP_ID)));
    Response r = resource.list(COLL_APP_ID, 0, 50);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<WatchIO> body = (PagedResponseIO<WatchIO>) r.getEntity();
    assertEquals(2, body.items().size());
    assertEquals("2", String.valueOf(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void list_pageSize1_returnsFirstItemOnly() {
    when(service.list(COLL_APP_ID))
      .thenReturn(List.of(watch(W1_APP_ID), watch(W2_APP_ID)));
    Response r = resource.list(COLL_APP_ID, 0, 1);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<WatchIO> body = (PagedResponseIO<WatchIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(W1_APP_ID, body.items().get(0).watchAppId());
    assertEquals("2", String.valueOf(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void list_pageSizeCappedAt200() {
    List<WatchIO> many = IntStream.range(0, 50)
      .mapToObj(i -> watch("018f9c5a-7e26-7000-b000-" + String.format("%012d", i)))
      .collect(Collectors.toList());
    when(service.list(COLL_APP_ID)).thenReturn(many);
    Response r = resource.list(COLL_APP_ID, 0, 999);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<WatchIO> body = (PagedResponseIO<WatchIO>) r.getEntity();
    assertTrue(body.items().size() <= 200, "pageSize must be capped at 200");
    assertEquals("50", String.valueOf(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void list_pagePastEnd_returnsEmpty() {
    when(service.list(COLL_APP_ID))
      .thenReturn(List.of(watch(W1_APP_ID), watch(W2_APP_ID)));
    Response r = resource.list(COLL_APP_ID, 99, 10);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<WatchIO> body = (PagedResponseIO<WatchIO>) r.getEntity();
    assertTrue(body.items().isEmpty(), "page past end must return empty list");
    assertEquals("2", String.valueOf(r.getHeaderString("X-Total-Count")));
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
}
