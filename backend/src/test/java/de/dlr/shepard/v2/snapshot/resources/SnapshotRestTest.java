package de.dlr.shepard.v2.snapshot.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.entities.SnapshotEntry;
import de.dlr.shepard.context.snapshot.io.SnapshotEntryIO;
import de.dlr.shepard.context.snapshot.io.SnapshotIO;
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
 * V2b — Mockito unit tests for {@link CollectionSnapshotRest} and
 * {@link SnapshotRest}.
 *
 * <p>No CDI container or network required; fields are injected directly.
 */
@SuppressWarnings("unchecked")
class SnapshotRestTest {

  // ── constants ────────────────────────────────────────────────────────────

  static final String COLL_APP_ID = "01900000-0000-7000-8000-000000000010";
  static final long COLL_OGM_ID = 10L;
  static final String SNAP_APP_ID = "01900000-0000-7000-8000-000000000020";
  static final long SNAP_OGM_ID = 20L;
  static final String CALLER = "alice";

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

  CollectionSnapshotRest collectionRest;
  SnapshotRest snapshotRest;

  // ── fixtures ─────────────────────────────────────────────────────────────

  Collection collection;
  Snapshot snapshot;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    // Wire the two REST resources
    collectionRest = new CollectionSnapshotRest();
    collectionRest.snapshotService = snapshotService;
    collectionRest.permissionsService = permissionsService;
    collectionRest.entityIdResolver = entityIdResolver;

    snapshotRest = new SnapshotRest();
    snapshotRest.snapshotService = snapshotService;
    snapshotRest.permissionsService = permissionsService;
    snapshotRest.entityIdResolver = entityIdResolver;

    // Build fixture entities
    collection = new Collection();
    collection.setId(COLL_OGM_ID);
    collection.setAppId(COLL_APP_ID);

    snapshot = new Snapshot();
    snapshot.setId(SNAP_OGM_ID);
    snapshot.setAppId(SNAP_APP_ID);
    snapshot.setName("v1.0");
    snapshot.setDescription("first release");
    snapshot.setSnapshotCapturedAtMs(System.currentTimeMillis());
    snapshot.setSnapshotCreatedByUsername(CALLER);
    snapshot.setCollection(collection);
    snapshot.setEntryCount(2);

    // Default stub: authenticated, authorised
    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), any(AccessType.class), eq(CALLER), eq(0L)))
      .thenReturn(true);
    when(snapshotService.findByAppId(SNAP_APP_ID)).thenReturn(snapshot);
  }

  // ── CollectionSnapshotRest — POST (create) ────────────────────────────────

  @Test
  void create_returns201_withCreatedSnapshot() {
    when(snapshotService.createSnapshot(COLL_APP_ID, "v1.0", "first release", CALLER)).thenReturn(snapshot);

    SnapshotIO body = new SnapshotIO(null, "v1.0", "first release", null, null, null, 0);
    Response r = collectionRest.create(COLL_APP_ID, body, sc);

    assertThat(r.getStatus()).isEqualTo(201);
    SnapshotIO io = (SnapshotIO) r.getEntity();
    assertThat(io.name()).isEqualTo("v1.0");
    assertThat(io.collectionAppId()).isEqualTo(COLL_APP_ID);
  }

  @Test
  void create_returns400_whenNameIsBlank() {
    SnapshotIO body = new SnapshotIO(null, "  ", null, null, null, null, 0);
    Response r = collectionRest.create(COLL_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(400);
    verify(snapshotService, never()).createSnapshot(any(), any(), any(), any());
  }

  @Test
  void create_returns400_whenBodyIsNull() {
    Response r = collectionRest.create(COLL_APP_ID, null, sc);
    assertThat(r.getStatus()).isEqualTo(400);
  }

  @Test
  void create_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    SnapshotIO body = new SnapshotIO(null, "v1.0", null, null, null, null, 0);
    Response r = collectionRest.create(COLL_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  @Test
  void create_returns403_whenNoWritePermission() {
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Write, CALLER, 0L))
      .thenReturn(false);
    SnapshotIO body = new SnapshotIO(null, "v1.0", null, null, null, null, 0);
    Response r = collectionRest.create(COLL_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(403);
  }

  @Test
  void create_returns404_whenCollectionNotFound() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    SnapshotIO body = new SnapshotIO(null, "v1.0", null, null, null, null, 0);
    Response r = collectionRest.create(COLL_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── CollectionSnapshotRest — GET (list) ──────────────────────────────────

  @Test
  void list_returns200WithRows() {
    when(snapshotService.listByCollection(COLL_APP_ID)).thenReturn(List.of(snapshot));

    Response r = collectionRest.list(COLL_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    List<SnapshotIO> body = (List<SnapshotIO>) r.getEntity();
    assertThat(body).hasSize(1);
    assertThat(body.get(0).appId()).isEqualTo(SNAP_APP_ID);
  }

  @Test
  void list_returns200WithEmptyList_whenNoSnapshots() {
    when(snapshotService.listByCollection(COLL_APP_ID)).thenReturn(List.of());
    Response r = collectionRest.list(COLL_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat((List<?>) r.getEntity()).isEmpty();
  }

  @Test
  void list_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = collectionRest.list(COLL_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  @Test
  void list_returns403_whenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER, 0L))
      .thenReturn(false);
    Response r = collectionRest.list(COLL_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(403);
  }

  @Test
  void list_returns404_whenCollectionNotFound() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    Response r = collectionRest.list(COLL_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── SnapshotRest — GET metadata ───────────────────────────────────────────

  @Test
  void read_returns200_withMetadata() {
    Response r = snapshotRest.read(SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    SnapshotIO io = (SnapshotIO) r.getEntity();
    assertThat(io.appId()).isEqualTo(SNAP_APP_ID);
    assertThat(io.name()).isEqualTo("v1.0");
    assertThat(io.entryCount()).isEqualTo(2);
  }

  @Test
  void read_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = snapshotRest.read(SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  @Test
  void read_returns404_whenSnapshotNotFound() {
    when(snapshotService.findByAppId(SNAP_APP_ID)).thenReturn(null);
    Response r = snapshotRest.read(SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void read_returns403_whenNoReadPermissionOnCollection() {
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER, 0L))
      .thenReturn(false);
    Response r = snapshotRest.read(SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(403);
  }

  // ── SnapshotRest — GET manifest ───────────────────────────────────────────

  @Test
  void manifest_returns200_withEntries() {
    SnapshotEntry e1 = new SnapshotEntry();
    e1.setEntityAppId("entity-app-id-1");
    e1.setRevision(3L);
    SnapshotEntry e2 = new SnapshotEntry();
    e2.setEntityAppId("entity-app-id-2");
    e2.setRevision(7L);
    when(snapshotService.findEntries(SNAP_OGM_ID)).thenReturn(List.of(e1, e2));

    Response r = snapshotRest.manifest(SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    List<SnapshotEntryIO> body = (List<SnapshotEntryIO>) r.getEntity();
    assertThat(body).hasSize(2);
    assertThat(body.get(0).entityAppId()).isEqualTo("entity-app-id-1");
    assertThat(body.get(0).revision()).isEqualTo(3L);
  }

  @Test
  void manifest_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = snapshotRest.manifest(SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  @Test
  void manifest_returns404_whenSnapshotNotFound() {
    when(snapshotService.findByAppId(SNAP_APP_ID)).thenReturn(null);
    Response r = snapshotRest.manifest(SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── SnapshotRest — DELETE ─────────────────────────────────────────────────

  @Test
  void delete_returns204_onSuccess() {
    when(snapshotService.deleteSnapshot(SNAP_APP_ID)).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Write, CALLER, 0L))
      .thenReturn(true);

    Response r = snapshotRest.delete(SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(204);
  }

  @Test
  void delete_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = snapshotRest.delete(SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  @Test
  void delete_returns403_whenNoWritePermission() {
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Write, CALLER, 0L))
      .thenReturn(false);
    Response r = snapshotRest.delete(SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(403);
  }

  @Test
  void delete_returns404_whenSnapshotNotFound() {
    when(snapshotService.findByAppId(SNAP_APP_ID)).thenReturn(null);
    Response r = snapshotRest.delete(SNAP_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }
}
