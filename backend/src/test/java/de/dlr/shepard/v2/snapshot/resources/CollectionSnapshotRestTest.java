package de.dlr.shepard.v2.snapshot.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CollectionSnapshotRest}.
 *
 * <p>Covers: OpenAPI parameter documentation regression + APISIMP-COLLECTION-SNAPSHOT-PLAIN-ARRAY
 * (list returns {@link PagedResponseIO} envelope, not a bare array).
 */
class CollectionSnapshotRestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000099";

  SnapshotService snapshotService;
  PermissionsService permissionsService;
  EntityIdResolver entityIdResolver;
  CollectionSnapshotRest resource;
  SecurityContext sc;

  @BeforeEach
  void setUp() {
    snapshotService = mock(SnapshotService.class);
    permissionsService = mock(PermissionsService.class);
    entityIdResolver = mock(EntityIdResolver.class);

    resource = new CollectionSnapshotRest();
    resource.snapshotService = snapshotService;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;

    sc = mock(SecurityContext.class);
    Principal p = () -> "testuser";
    when(sc.getUserPrincipal()).thenReturn(p);
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(1L);
    when(permissionsService.isAccessTypeAllowedForUser(anyLong(), any(), any(), anyLong())).thenReturn(true);
  }

  @Test
  void list_returnsPagedEnvelope() {
    Snapshot s = new Snapshot();
    s.setName("v1.0");
    when(snapshotService.listByCollection(COLL_APP_ID, 0, 50)).thenReturn(List.of(s));
    when(snapshotService.countByCollection(COLL_APP_ID)).thenReturn(1L);

    Response resp = resource.list(COLL_APP_ID, 0, 50, sc);

    assertEquals(200, resp.getStatus());
    assertInstanceOf(PagedResponseIO.class, resp.getEntity());
    PagedResponseIO<?> paged = (PagedResponseIO<?>) resp.getEntity();
    assertEquals(1, paged.items().size());
    assertEquals(1L, paged.total());
    assertEquals(0, paged.page());
    assertEquals(50, paged.pageSize());
  }

  @Test
  void list_emptyCollection_returnsZeroTotal() {
    when(snapshotService.listByCollection(COLL_APP_ID, 0, 50)).thenReturn(List.of());
    when(snapshotService.countByCollection(COLL_APP_ID)).thenReturn(0L);

    Response resp = resource.list(COLL_APP_ID, 0, 50, sc);

    assertEquals(200, resp.getStatus());
    PagedResponseIO<?> paged = (PagedResponseIO<?>) resp.getEntity();
    assertEquals(0, paged.items().size());
    assertEquals(0L, paged.total());
  }

  private static java.lang.reflect.Parameter listParam(String qpName) throws NoSuchMethodException {
    java.lang.reflect.Method m = CollectionSnapshotRest.class.getMethod(
        "list", String.class, int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    return java.util.Arrays.stream(m.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && qpName.equals(qp.value());
        })
        .findFirst()
        .orElseThrow(() -> new AssertionError("No @QueryParam(\"" + qpName + "\") on list()"));
  }

  private static void assertParamDocumented(java.lang.reflect.Parameter param, String label) {
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, label + " must carry @Parameter");
    assertTrue(ann.description() != null && !ann.description().isBlank(),
        label + " @Parameter.description must be non-blank");
  }

  @Test
  void list_pageParamIsDocumented() throws NoSuchMethodException {
    assertParamDocumented(listParam("page"), "list.page");
  }

  @Test
  void list_pageSizeParamIsDocumented() throws NoSuchMethodException {
    assertParamDocumented(listParam("pageSize"), "list.pageSize");
  }
}
