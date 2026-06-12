package de.dlr.shepard.v2.snapshot.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import de.dlr.shepard.v2.snapshot.resources.SnapshotListRest.SnapshotListPageIO;
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
 * scoped filtering, pagination envelope shape, 401 unauthenticated, 404 for
 * unknown collectionAppId.
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

  // ── basic shape ───────────────────────────────────────────────────────────

  @Test
  void list_returns200_withEmptyEnvelope_whenNoSnapshots() {
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of());
    when(snapshotService.countAll()).thenReturn(0L);

    Response r = rest.list(null, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO body = (SnapshotListPageIO) r.getEntity();
    assertThat(body.items()).isEmpty();
    assertThat(body.total()).isEqualTo(0L);
    assertThat(body.page()).isEqualTo(0);
    assertThat(body.pageSize()).isEqualTo(50);
  }

  @Test
  void list_returns200_withPopulatedItems_andCollectionMetadata() {
    Collection c = coll(COLL_A_APP, "LUMEN campaign");
    Snapshot s = snap("snap-1", "v1.0", c);
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of(s));
    when(snapshotService.countAll()).thenReturn(1L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_A_OGM), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);

    Response r = rest.list(null, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO body = (SnapshotListPageIO) r.getEntity();
    assertThat(body.items()).hasSize(1);
    var item = body.items().get(0);
    assertThat(item.appId()).isEqualTo("snap-1");
    assertThat(item.name()).isEqualTo("v1.0");
    assertThat(item.collectionAppId()).isEqualTo(COLL_A_APP);
    assertThat(item.collectionName()).isEqualTo("LUMEN campaign");
  }

  // ── permission scoping ───────────────────────────────────────────────────

  @Test
  void list_filtersOutSnapshotsTheCallerCannotRead() {
    Collection cA = coll(COLL_A_APP, "A");
    Collection cB = coll(COLL_B_APP, "B");
    Snapshot a = snap("snap-a", "Snap A", cA);
    Snapshot b = snap("snap-b", "Snap B", cB);
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of(a, b));
    when(snapshotService.countAll()).thenReturn(2L);
    // Caller can read A but not B.
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_A_OGM), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_B_OGM), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);

    Response r = rest.list(null, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO body = (SnapshotListPageIO) r.getEntity();
    assertThat(body.items()).hasSize(1);
    assertThat(body.items().get(0).appId()).isEqualTo("snap-a");
    // Total still reports the unfiltered count per the class Javadoc.
    assertThat(body.total()).isEqualTo(2L);
  }

  @Test
  void list_skipsSnapshotsWithoutAttachedCollection() {
    Snapshot orphan = snap("snap-orphan", "orphan", null);
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of(orphan));
    when(snapshotService.countAll()).thenReturn(1L);

    Response r = rest.list(null, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO body = (SnapshotListPageIO) r.getEntity();
    assertThat(body.items()).isEmpty();
  }

  // ── collection-scoped variant ─────────────────────────────────────────────

  @Test
  void list_scopedToCollection_callsListByCollection() {
    Collection cA = coll(COLL_A_APP, "A");
    Snapshot s = snap("snap-a", "v1.0", cA);
    when(snapshotService.listByCollection(eq(COLL_A_APP), anyInt(), anyInt())).thenReturn(List.of(s));
    when(snapshotService.countByCollection(COLL_A_APP)).thenReturn(1L);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_A_OGM), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);

    Response r = rest.list(COLL_A_APP, 0, 50, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO body = (SnapshotListPageIO) r.getEntity();
    assertThat(body.items()).hasSize(1);
    assertThat(body.total()).isEqualTo(1L);
    verify(snapshotService).listByCollection(eq(COLL_A_APP), anyInt(), anyInt());
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
    when(snapshotService.countAll()).thenReturn(0L);
    Response r = rest.list(null, -3, 9999, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO body = (SnapshotListPageIO) r.getEntity();
    assertThat(body.page()).isEqualTo(0);
    assertThat(body.pageSize()).isEqualTo(200);
  }

  @Test
  void list_echoesValidPagination() {
    when(snapshotService.listAll(anyInt(), anyInt())).thenReturn(List.of());
    when(snapshotService.countAll()).thenReturn(0L);
    Response r = rest.list(null, 3, 25, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotListPageIO body = (SnapshotListPageIO) r.getEntity();
    assertThat(body.page()).isEqualTo(3);
    assertThat(body.pageSize()).isEqualTo(25);
  }
}
