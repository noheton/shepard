package de.dlr.shepard.v2.snapshot.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Regression tests for {@link CollectionSnapshotRest}.
 *
 * <p>Covers:
 * <ul>
 *   <li>OpenAPI {@code @Parameter} documentation on {@code list()} query params.</li>
 *   <li>APISIMP-COLLECTION-SNAPSHOT-PLAIN-ARRAY — {@code list()} must return a
 *       {@link PagedResponseIO} envelope, not a plain array.</li>
 * </ul>
 */
class CollectionSnapshotRestTest {

  // ── Mockito-based test for the PagedResponseIO envelope ───────────────────

  @Mock SnapshotService snapshotService;
  @Mock PermissionsService permissionsService;
  @Mock EntityIdResolver entityIdResolver;
  @Mock SecurityContext securityContext;
  @Mock Principal principal;

  CollectionSnapshotRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionSnapshotRest();
    resource.snapshotService = snapshotService;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;
  }

  private void stubAccessAllowed(String collectionAppId) {
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn("testuser");
    when(entityIdResolver.resolveLong(collectionAppId)).thenReturn(1L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(1L), any(), eq("testuser"), anyLong())).thenReturn(true);
  }

  /** APISIMP-COLLECTION-SNAPSHOT-PLAIN-ARRAY — response must be a PagedResponseIO envelope. */
  @Test
  void list_returnsPagedEnvelope() {
    String cid = "018f9c5a-0000-7000-a000-000000000001";
    stubAccessAllowed(cid);

    Snapshot snap = new Snapshot();
    snap.setAppId("018f9c5a-0000-7000-a000-000000000099");
    snap.setName("v1.0");
    when(snapshotService.listByCollection(eq(cid), anyInt(), anyInt())).thenReturn(List.of(snap));
    when(snapshotService.countByCollection(cid)).thenReturn(1L);

    Response resp = resource.list(cid, 0, 50, securityContext);

    assertEquals(200, resp.getStatus());
    Object body = resp.getEntity();
    assertNotNull(body, "Response body must not be null");
    assertTrue(body instanceof PagedResponseIO, "Response entity must be PagedResponseIO, got: " + body.getClass());

    @SuppressWarnings("unchecked")
    PagedResponseIO<Object> page = (PagedResponseIO<Object>) body;
    assertEquals(1L, page.total(), "total must equal countByCollection result");
    assertEquals(1, page.items().size(), "items must contain the one snapshot");
    assertEquals(0, page.page());
    assertEquals(50, page.pageSize());
  }

  @Test
  void list_emptyCollection_returnsZeroTotal() {
    String cid = "018f9c5a-0000-7000-a000-000000000002";
    stubAccessAllowed(cid);
    when(snapshotService.listByCollection(eq(cid), anyInt(), anyInt())).thenReturn(List.of());
    when(snapshotService.countByCollection(cid)).thenReturn(0L);

    Response resp = resource.list(cid, 0, 50, securityContext);

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<Object> page = (PagedResponseIO<Object>) resp.getEntity();
    assertEquals(0L, page.total());
    assertTrue(page.items().isEmpty());
  }

  // ── Reflection-based OpenAPI parameter tests ──────────────────────────────

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
