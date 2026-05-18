package de.dlr.shepard.context.snapshot.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.snapshot.daos.SnapshotDAO;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.entities.SnapshotEntry;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * V2b — unit tests for {@link SnapshotService}.
 *
 * <p>No CDI or Neo4j required; all dependencies are Mockito mocks injected
 * directly into the service.
 */
class SnapshotServiceTest {

  static final String COLL_APP_ID = "01900000-0000-7000-8000-000000000010";
  static final String SNAP_APP_ID = "01900000-0000-7000-8000-000000000020";
  static final String CALLER = "alice";

  @Mock
  SnapshotDAO snapshotDAO;

  @Mock
  CollectionDAO collectionDAO;

  SnapshotService service;

  Collection collection;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new SnapshotService();
    service.snapshotDAO = snapshotDAO;
    service.collectionDAO = collectionDAO;

    collection = new Collection();
    collection.setId(10L);
    collection.setAppId(COLL_APP_ID);

    // Default: collectionDAO finds the collection
    when(
      collectionDAO.findByQuery(anyString(), any())
    ).thenReturn(List.of(collection));

    // Default: snapshotDAO.createOrUpdate echoes back the argument
    when(snapshotDAO.createOrUpdate(any(Snapshot.class))).thenAnswer(inv -> inv.getArgument(0));
    when(snapshotDAO.createEntry(any(SnapshotEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    when(snapshotDAO.walkCollectionSubtree(COLL_APP_ID)).thenReturn(List.of());
  }

  // ── createSnapshot ────────────────────────────────────────────────────────

  @Test
  void createSnapshot_persistsSnapshotWithCorrectFields() {
    Snapshot created = service.createSnapshot(COLL_APP_ID, "v1.0", "first", CALLER);

    assertThat(created.getName()).isEqualTo("v1.0");
    assertThat(created.getDescription()).isEqualTo("first");
    assertThat(created.getSnapshotCreatedByUsername()).isEqualTo(CALLER);
    assertThat(created.getCollection()).isSameAs(collection);
    assertThat(created.getSnapshotCapturedAtMs()).isGreaterThan(0L);
  }

  @Test
  void createSnapshot_createsOneEntryPerEntityInSubtree() {
    when(snapshotDAO.walkCollectionSubtree(COLL_APP_ID)).thenReturn(
      List.of(
        Map.of("entityAppId", "entity-1", "revision", 3L),
        Map.of("entityAppId", "entity-2", "revision", 7L)
      )
    );

    Snapshot created = service.createSnapshot(COLL_APP_ID, "v1.0", null, CALLER);

    assertThat(created.getEntryCount()).isEqualTo(2);
    // Verify createEntry was called twice
    ArgumentCaptor<SnapshotEntry> captor = ArgumentCaptor.forClass(SnapshotEntry.class);
    verify(snapshotDAO, times(2)).createEntry(captor.capture());
    List<SnapshotEntry> entries = captor.getAllValues();
    assertThat(entries).extracting(SnapshotEntry::getEntityAppId)
      .containsExactlyInAnyOrder("entity-1", "entity-2");
    assertThat(entries).extracting(SnapshotEntry::getRevision)
      .containsExactlyInAnyOrder(3L, 7L);
  }

  @Test
  void createSnapshot_throwsNotFound_whenCollectionMissing() {
    when(collectionDAO.findByQuery(anyString(), any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.createSnapshot("bad-id", "v1.0", null, CALLER))
      .isInstanceOf(NotFoundException.class);
  }

  @Test
  void createSnapshot_setsEntryCountToZero_whenSubtreeIsEmpty() {
    // walkCollectionSubtree returns empty list (already the default mock)
    Snapshot created = service.createSnapshot(COLL_APP_ID, "empty", null, CALLER);
    assertThat(created.getEntryCount()).isEqualTo(0);
    verify(snapshotDAO, times(0)).createEntry(any());
  }

  // ── listByCollection ──────────────────────────────────────────────────────

  @Test
  void listByCollection_delegatesToDAO_withPagination() {
    Snapshot s = new Snapshot();
    s.setAppId(SNAP_APP_ID);
    when(snapshotDAO.findByCollectionAppId(COLL_APP_ID, 0, 50)).thenReturn(List.of(s));

    List<Snapshot> result = service.listByCollection(COLL_APP_ID, 0, 50);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getAppId()).isEqualTo(SNAP_APP_ID);
  }

  // ── listDataObjectAppIds (V2c) ────────────────────────────────────────────

  @Test
  void listDataObjectAppIds_delegatesGetEntryAppIdsAndFilter() {
    Snapshot snap = new Snapshot();
    snap.setId(20L);
    snap.setAppId(SNAP_APP_ID);

    String doAppId1 = "01900000-0000-7000-8000-000000000030";
    String doAppId2 = "01900000-0000-7000-8000-000000000040";
    String collAppId = "01900000-0000-7000-8000-000000000050"; // a Collection in the snapshot, excluded

    Set<String> allEntries = Set.of(doAppId1, doAppId2, collAppId);
    when(snapshotDAO.getEntryAppIds(20L)).thenReturn(allEntries);
    when(snapshotDAO.filterDataObjectAppIds(allEntries)).thenReturn(List.of(doAppId1, doAppId2));

    List<String> result = service.listDataObjectAppIds(snap);

    assertThat(result).containsExactly(doAppId1, doAppId2);
    verify(snapshotDAO).getEntryAppIds(20L);
    verify(snapshotDAO).filterDataObjectAppIds(allEntries);
  }

  @Test
  void listDataObjectAppIds_returnsEmptyList_whenSnapshotHasNoEntries() {
    Snapshot snap = new Snapshot();
    snap.setId(20L);
    snap.setAppId(SNAP_APP_ID);

    when(snapshotDAO.getEntryAppIds(20L)).thenReturn(Set.of());
    when(snapshotDAO.filterDataObjectAppIds(Set.of())).thenReturn(List.of());

    List<String> result = service.listDataObjectAppIds(snap);

    assertThat(result).isEmpty();
  }

  // ── deleteSnapshot ────────────────────────────────────────────────────────

  @Test
  void deleteSnapshot_returnsFalse_whenNotFound() {
    when(snapshotDAO.findByAppId("missing")).thenReturn(null);
    boolean result = service.deleteSnapshot("missing");
    assertThat(result).isFalse();
  }

  @Test
  void deleteSnapshot_softDeletesSnapshotAndEntries() {
    Snapshot snap = new Snapshot();
    snap.setId(50L);
    snap.setAppId(SNAP_APP_ID);
    when(snapshotDAO.findByAppId(SNAP_APP_ID)).thenReturn(snap);
    when(snapshotDAO.createOrUpdate(any(Snapshot.class))).thenAnswer(inv -> inv.getArgument(0));

    SnapshotEntry entry = new SnapshotEntry();
    entry.setEntityAppId("e-1");
    when(snapshotDAO.findEntriesBySnapshot(50L)).thenReturn(List.of(entry));

    boolean result = service.deleteSnapshot(SNAP_APP_ID);
    assertThat(result).isTrue();
    assertThat(snap.isDeleted()).isTrue();
    assertThat(entry.isDeleted()).isTrue();
  }
}
