package de.dlr.shepard.v2.snapshot.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import java.util.Arrays;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.io.SnapshotListItemIO;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import de.dlr.shepard.v2.snapshot.io.SnapshotListPageIO;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SNAPSHOT-LIST-1-REST — unit tests for {@link SnapshotListRest}.
 *
 * <p>Covers: empty + populated lists, permission-scoped filtering, collection-
 * scoped filtering, hasMore flag, pagination envelope shape, 401 unauthenticated,
 * 404 for unknown collectionAppId, 200-empty for forbidden collection.
 *
 * <p>APISIMP-SNAPSHOT-LIST-TOTAL: the old tests that asserted
 * {@code body.total() == unfilteredCount} are replaced by {@code hasMore}
 * assertions — the response no longer carries a misleading unfiltered total.
 */
class SnapshotListRestTest {

  private static final String CALLER = "alice";
  private static final String COLL_A_APP = "01900000-0000-7000-8000-0000000000a1";
  private static final long COLL_A_OGM = 1001L;
  private static final String COLL_B_APP = "01900000-0000-7000-8000-0000000000b1";
  private static final long COLL_B_OGM = 1002L;

  private SnapshotListRest rest;
  private SnapshotService snapshotService;
  private PermissionsService permissionsService;
  private EntityIdResolver entityIdResolver;
  private SecurityContext sc;
  private Principal principal;

  @BeforeEach
  void setUp() {
    rest = new SnapshotListRest();
    snapshotService = mock(SnapshotService.class);
    permissionsService = mock(PermissionsService.class);
    entityIdResolver = mock(EntityIdResolver.class);
    sc = mock(SecurityContext.class);
    principal = mock(Principal.class);

    rest.snapshotService = snapshotService;
    rest.permissionsService = permissionsService;
    rest.entityIdResolver = entityIdResolver;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(COLL_A_APP)).thenReturn(COLL_A_OGM);
    when(entityIdResolver.resolveLong(COLL_B_APP)).thenReturn(COLL_B_OGM);
  }

  private Snapshot snap(String appId, String name, Collection coll) {
    Snapshot s = new Snapshot();
    s.setAppId(appId);
    s.setName(name);
    s.setSnapshotCapturedAtMs(1_000L);
    s.setCollection(coll);
    return s;
  }

  private Collection coll(String appId, String name) {
    Collection c = new Collection();
    c.setAppId(appId);
    c.setName(name);
    return c;
  }

  @SuppressWarnings("unchecked")
  private SnapshotListPageIO<SnapshotListItemIO> body(Response r) {
    return (SnapshotListPageIO<SnapshotListItemIO>) r.getEntity();
  }

  // ── basic shape ───────────────────────────────────────────────────────────

  @Test
  void list_returns200_withEmptyEnvelope_whenNoSnapshots() {
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of());

    Response r = rest.list(null, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO<SnapshotListItemIO> b = body(r);
    assertThat(b.items()).isEmpty();
    assertThat(b.page()).isEqualTo(0);
    assertThat(b.pageSize()).isEqualTo(50);
    assertThat(b.hasMore()).isFalse();
  }

  @Test
  void list_returns200_withPopulatedItems_andCollectionMetadata() {
    Collection c = coll(COLL_A_APP, "LUMEN campaign");
    Snapshot s = snap("snap-1", "v1.0", c);
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of(s));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_A_OGM), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);

    Response r = rest.list(null, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO<SnapshotListItemIO> b = body(r);
    assertThat(b.items()).hasSize(1);
    var item = b.items().get(0);
    assertThat(item.appId()).isEqualTo("snap-1");
    assertThat(item.name()).isEqualTo("v1.0");
    assertThat(item.collectionAppId()).isEqualTo(COLL_A_APP);
    assertThat(item.collectionName()).isEqualTo("LUMEN campaign");
  }

  // ── hasMore flag ──────────────────────────────────────────────────────────

  @Test
  void list_hasMore_isFalse_whenRawPageSmallerThanPageSize() {
    // DB returned 3 items for pageSize=50 → clearly last page.
    Collection c = coll(COLL_A_APP, "A");
    when(snapshotService.listAll(anyInt(), anyInt()))
        .thenReturn(List.of(snap("a", "a", c), snap("b", "b", c), snap("cc", "cc", c)));
    when(permissionsService.isAccessTypeAllowedForUser(anyLong(), any(), anyString(), anyLong()))
        .thenReturn(true);

    Response r = rest.list(null, 0, 50, sc);
    assertThat(body(r).hasMore()).isFalse();
  }

  @Test
  void list_hasMore_isTrue_whenRawPageEqualsPageSize() {
    // DB returned exactly pageSize=2 items → there may be more.
    Collection c = coll(COLL_A_APP, "A");
    when(snapshotService.listAll(anyInt(), anyInt()))
        .thenReturn(List.of(snap("a", "a", c), snap("b", "b", c)));
    when(permissionsService.isAccessTypeAllowedForUser(anyLong(), any(), anyString(), anyLong()))
        .thenReturn(true);

    Response r = rest.list(null, 0, 2, sc);
    assertThat(body(r).hasMore()).isTrue();
  }

  // ── permission scoping ───────────────────────────────────────────────────

  @Test
  void list_filtersOutSnapshotsTheCallerCannotRead() {
    Collection cA = coll(COLL_A_APP, "A");
    Collection cB = coll(COLL_B_APP, "B");
    Snapshot a = snap("snap-a", "Snap A", cA);
    Snapshot b = snap("snap-b", "Snap B", cB);
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of(a, b));
    // Caller can read A but not B.
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_A_OGM), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_B_OGM), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);

    Response r = rest.list(null, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO<SnapshotListItemIO> page = body(r);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).appId()).isEqualTo("snap-a");
    // APISIMP-SNAPSHOT-LIST-TOTAL: no longer reports misleading unfiltered total.
    // hasMore reflects whether the raw DB page was full (2 < 50 → false).
    assertThat(page.hasMore()).isFalse();
  }

  @Test
  void list_skipsSnapshotsWithoutAttachedCollection() {
    Snapshot orphan = snap("snap-orphan", "orphan", null);
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of(orphan));

    Response r = rest.list(null, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(body(r).items()).isEmpty();
  }

  // ── collection-scoped variant ─────────────────────────────────────────────

  @Test
  void list_scopedToCollection_callsListByCollection() {
    Collection cA = coll(COLL_A_APP, "A");
    Snapshot s = snap("snap-a", "v1.0", cA);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_A_OGM), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(snapshotService.listByCollection(eq(COLL_A_APP), anyInt(), anyInt())).thenReturn(List.of(s));

    Response r = rest.list(COLL_A_APP, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO<SnapshotListItemIO> b = body(r);
    assertThat(b.items()).hasSize(1);
    // 1 < 50 → hasMore=false (accurate since all collection items are visible)
    assertThat(b.hasMore()).isFalse();
    verify(snapshotService).listByCollection(eq(COLL_A_APP), anyInt(), anyInt());
  }

  @Test
  void list_scopedToCollection_returnsEmptyPage_whenCallerCannotReadCollection() {
    // Collection exists but caller has no Read → empty page, no DB snapshot fetch.
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_A_OGM), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);

    Response r = rest.list(COLL_A_APP, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO<SnapshotListItemIO> b = body(r);
    assertThat(b.items()).isEmpty();
    assertThat(b.hasMore()).isFalse();
    // Must NOT have fetched snapshots at all.
    verify(snapshotService, never()).listByCollection(anyString(), anyInt(), anyInt());
  }

  @Test
  void list_scopedToUnknownCollection_returns404() {
    when(entityIdResolver.resolveLong("missing")).thenThrow(new NotFoundException());
    Response r = rest.list("missing", 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── auth ──────────────────────────────────────────────────────────────────

  @Test
  void list_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = rest.list(null, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  // ── pagination clamping ───────────────────────────────────────────────────

  @Test
  void list_clampsPageAndSize() {
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of());
    Response r = rest.list(null, -3, 9999, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO<SnapshotListItemIO> b = body(r);
    assertThat(b.page()).isEqualTo(0);
    assertThat(b.pageSize()).isEqualTo(200);
  }

  @Test
  void list_echoesValidPagination() {
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of());
    Response r = rest.list(null, 3, 25, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO<SnapshotListItemIO> b = body(r);
    assertThat(b.page()).isEqualTo(3);
    assertThat(b.pageSize()).isEqualTo(25);
  }

  // ── APISIMP-SNAPSHOT-LIST-N+1 ────────────────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void list_batchResolvesCollectionIds_inSingleCall() {
    Collection cA = coll(COLL_A_APP, "A");
    Collection cB = coll(COLL_B_APP, "B");
    Snapshot a = snap("snap-a", "Snap A", cA);
    Snapshot b = snap("snap-b", "Snap B", cB);
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of(a, b));
    when(permissionsService.isAccessTypeAllowedForUser(anyLong(), any(), anyString(), anyLong()))
      .thenReturn(true);

    rest.list(null, 0, 50, sc);

    ArgumentCaptor<java.util.Collection<String>> captor =
      ArgumentCaptor.forClass(java.util.Collection.class);
    verify(entityIdResolver).resolveLongs(captor.capture());
    assertThat(captor.getValue()).containsExactlyInAnyOrder(COLL_A_APP, COLL_B_APP);
  }

  // ─── APISIMP-SNAPSHOT-LIST-PARAMS-UNDOCUMENTED regression ────────────────

  private java.lang.reflect.Parameter findListParam(String queryParamName) throws NoSuchMethodException {
    java.lang.reflect.Method method = SnapshotListRest.class.getMethod(
        "list", String.class, int.class, int.class, jakarta.ws.rs.core.SecurityContext.class);
    return Arrays.stream(method.getParameters())
        .filter(p -> {
          jakarta.ws.rs.QueryParam qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && queryParamName.equals(qp.value());
        })
        .findFirst()
        .orElse(null);
  }

  @Test
  void list_collectionAppIdParam_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Parameter param = findListParam("collectionAppId");
    assertNotNull(param, "collectionAppId must carry @QueryParam");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "collectionAppId must carry @Parameter");
    assertFalse(ann.description().isBlank(), "@Parameter.description must be non-blank for collectionAppId");
  }

  @Test
  void list_pageParam_hasParameterAnnotationWithDescription() throws NoSuchMethodException {
    java.lang.reflect.Parameter param = findListParam("page");
    assertNotNull(param, "page must carry @QueryParam");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "page must carry @Parameter");
    assertFalse(ann.description().isBlank(), "@Parameter.description must be non-blank for page");
  }

  @Test
  void list_pageSizeParam_hasParameterAnnotationDocumentingClamp() throws NoSuchMethodException {
    java.lang.reflect.Parameter param = findListParam("pageSize");
    assertNotNull(param, "pageSize must carry @QueryParam");
    var ann = param.getAnnotation(org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "pageSize must carry @Parameter");
    assertFalse(ann.description().isBlank(), "@Parameter.description must be non-blank for pageSize");
    assertThat(ann.description()).contains("200");
  }
}
