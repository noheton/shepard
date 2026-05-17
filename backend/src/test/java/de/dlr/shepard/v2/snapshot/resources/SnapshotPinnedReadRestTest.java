package de.dlr.shepard.v2.snapshot.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.io.SnapshotDataObjectsIO;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * V2c — Mockito unit tests for {@link SnapshotPinnedReadRest}.
 *
 * <p>No CDI container or network required; fields are injected directly.
 * Gate order tested: 401 → 404 (collection) → 403 → 404 (snapshot) → 409
 * (collection mismatch) → 200.
 */
class SnapshotPinnedReadRestTest {

  // ── constants ────────────────────────────────────────────────────────────

  static final String COLL_APP_ID = "01900000-0000-7000-8000-000000000010";
  static final long COLL_OGM_ID = 10L;
  static final String OTHER_COLL_APP_ID = "01900000-0000-7000-8000-000000000099";
  static final String SNAP_APP_ID = "01900000-0000-7000-8000-000000000020";
  static final long SNAP_OGM_ID = 20L;
  static final String DO_APP_ID_1 = "01900000-0000-7000-8000-000000000030";
  static final String DO_APP_ID_2 = "01900000-0000-7000-8000-000000000040";
  static final String CALLER = "alice";
  static final long CAPTURED_AT_MS = 1_747_000_000_000L;

  // ── mocks ────────────────────────────────────────────────────────────────

  @Mock
  SnapshotService snapshotService;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  // ── subject under test ────────────────────────────────────────────────────

  SnapshotPinnedReadRest rest;

  // ── fixtures ─────────────────────────────────────────────────────────────

  Collection collection;
  Snapshot snapshot;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    rest = new SnapshotPinnedReadRest();
    rest.snapshotService = snapshotService;
    rest.permissionsService = permissionsService;
    rest.entityIdResolver = entityIdResolver;

    // Build fixture entities
    collection = new Collection();
    collection.setId(COLL_OGM_ID);
    collection.setAppId(COLL_APP_ID);

    snapshot = new Snapshot();
    snapshot.setId(SNAP_OGM_ID);
    snapshot.setAppId(SNAP_APP_ID);
    snapshot.setName("v1.0");
    snapshot.setDescription("first release");
    snapshot.setSnapshotCapturedAtMs(CAPTURED_AT_MS);
    snapshot.setSnapshotCreatedByUsername(CALLER);
    snapshot.setCollection(collection);
    snapshot.setEntryCount(5);

    // Default stubs: authenticated, authorised, snapshot found
    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), any(AccessType.class), eq(CALLER), eq(0L)))
      .thenReturn(true);
    when(snapshotService.findByAppId(SNAP_APP_ID)).thenReturn(snapshot);
    when(snapshotService.listDataObjectAppIds(snapshot)).thenReturn(List.of(DO_APP_ID_1, DO_APP_ID_2));
  }

  // ── 401 — unauthenticated ─────────────────────────────────────────────────

  @Test
  void returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = rest.getDataObjects(COLL_APP_ID, SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  // ── 404 — collection not found ────────────────────────────────────────────

  @Test
  void returns404_whenCollectionNotFound() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    Response r = rest.getDataObjects(COLL_APP_ID, SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── 403 — no Read permission ──────────────────────────────────────────────

  @Test
  void returns403_whenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER, 0L))
      .thenReturn(false);
    Response r = rest.getDataObjects(COLL_APP_ID, SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(403);
  }

  // ── 404 — snapshot not found ──────────────────────────────────────────────

  @Test
  void returns404_whenSnapshotNotFound() {
    when(snapshotService.findByAppId(SNAP_APP_ID)).thenReturn(null);
    Response r = rest.getDataObjects(COLL_APP_ID, SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── 409 — snapshot belongs to a different collection ─────────────────────

  @Test
  void returns409_whenSnapshotBelongsToDifferentCollection() {
    // The snapshot's root collection has a different appId
    Collection otherCollection = new Collection();
    otherCollection.setId(99L);
    otherCollection.setAppId(OTHER_COLL_APP_ID);
    snapshot.setCollection(otherCollection);

    Response r = rest.getDataObjects(COLL_APP_ID, SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(409);
  }

  @Test
  void returns409_whenSnapshotHasNullCollection() {
    snapshot.setCollection(null);
    Response r = rest.getDataObjects(COLL_APP_ID, SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(409);
  }

  // ── 200 — happy paths ────────────────────────────────────────────────────

  @Test
  void returns200_withEmptyList_whenNoDataObjectsInSnapshot() {
    when(snapshotService.listDataObjectAppIds(snapshot)).thenReturn(List.of());
    Response r = rest.getDataObjects(COLL_APP_ID, SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotDataObjectsIO body = (SnapshotDataObjectsIO) r.getEntity();
    assertThat(body.dataObjectAppIds()).isEmpty();
    assertThat(body.totalEntries()).isEqualTo(5);
  }

  @Test
  void returns200_withCorrectDataObjectAppIds() {
    Response r = rest.getDataObjects(COLL_APP_ID, SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotDataObjectsIO body = (SnapshotDataObjectsIO) r.getEntity();
    assertThat(body.dataObjectAppIds()).containsExactly(DO_APP_ID_1, DO_APP_ID_2);
  }

  @Test
  void returns200_snapshotMetadataFieldsAreCorrect() {
    Response r = rest.getDataObjects(COLL_APP_ID, SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotDataObjectsIO body = (SnapshotDataObjectsIO) r.getEntity();
    assertThat(body.snapshotAppId()).isEqualTo(SNAP_APP_ID);
    assertThat(body.collectionAppId()).isEqualTo(COLL_APP_ID);
    assertThat(body.snapshotName()).isEqualTo("v1.0");
    assertThat(body.snapshotCapturedAt().toEpochMilli()).isEqualTo(CAPTURED_AT_MS);
    assertThat(body.totalEntries()).isEqualTo(5);
  }
}
