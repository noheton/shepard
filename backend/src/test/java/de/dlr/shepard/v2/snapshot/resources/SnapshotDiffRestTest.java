package de.dlr.shepard.v2.snapshot.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.context.snapshot.io.SnapshotDiffIO;
import de.dlr.shepard.context.snapshot.io.SnapshotDiffIO.DiffEntry;
import de.dlr.shepard.context.snapshot.services.SnapshotService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * V2e — Mockito unit tests for {@link SnapshotDiffRest}.
 *
 * <p>No CDI container or network required; fields are injected directly.
 * Covers: 401 unauthenticated, 400 self-diff, 404 A not found, 404 B not found,
 * 200 correct added list, 200 correct removed list, 200 correct changed list,
 * 200 correct unchangedCount, 200 lists are sorted by entityAppId,
 * 200 truncation when maxItems exceeded (APISIMP-SNAPSHOT-DIFF-UNCAPPED),
 * 200 truncated=false when diff fits within maxItems.
 */
class SnapshotDiffRestTest {

  // ── constants ────────────────────────────────────────────────────────────

  static final String SNAP_A_APP_ID = "01900000-0000-7000-8000-000000000020";
  static final long SNAP_A_OGM_ID = 20L;
  static final long SNAP_A_CAPTURED_AT = 1_700_000_000_000L;

  static final String SNAP_B_APP_ID = "01900000-0000-7000-8000-000000000021";
  static final long SNAP_B_OGM_ID = 21L;
  static final long SNAP_B_CAPTURED_AT = 1_700_001_000_000L;

  static final String CALLER = "alice";
  static final int DEFAULT_MAX = SnapshotDiffRest.DEFAULT_MAX_ITEMS;

  // ── mocks ────────────────────────────────────────────────────────────────

  @Mock
  SnapshotService snapshotService;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  SnapshotDiffRest diffRest;

  // ── fixtures ─────────────────────────────────────────────────────────────

  Snapshot snapshotA;
  Snapshot snapshotB;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    diffRest = new SnapshotDiffRest();
    diffRest.snapshotService = snapshotService;

    snapshotA = new Snapshot();
    snapshotA.setId(SNAP_A_OGM_ID);
    snapshotA.setAppId(SNAP_A_APP_ID);
    snapshotA.setName("v1.0");
    snapshotA.setSnapshotCapturedAtMs(SNAP_A_CAPTURED_AT);

    snapshotB = new Snapshot();
    snapshotB.setId(SNAP_B_OGM_ID);
    snapshotB.setAppId(SNAP_B_APP_ID);
    snapshotB.setName("v2.0");
    snapshotB.setSnapshotCapturedAtMs(SNAP_B_CAPTURED_AT);

    // Default stubs: authenticated, both snapshots found, empty maps
    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(snapshotService.findByAppId(SNAP_A_APP_ID)).thenReturn(snapshotA);
    when(snapshotService.findByAppId(SNAP_B_APP_ID)).thenReturn(snapshotB);
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM_ID)).thenReturn(Map.of());
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM_ID)).thenReturn(Map.of());
  }

  // ── 401 unauthenticated ───────────────────────────────────────────────────

  @Test
  void diff_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, DEFAULT_MAX);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  // ── 400 self-diff ─────────────────────────────────────────────────────────

  @Test
  void diff_returns400_whenAandBAreSameAppId() {
    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_A_APP_ID, sc, DEFAULT_MAX);
    assertThat(r.getStatus()).isEqualTo(400);
  }

  // ── 404 snapshot not found ────────────────────────────────────────────────

  @Test
  void diff_returns404_whenSnapshotANotFound() {
    when(snapshotService.findByAppId(SNAP_A_APP_ID)).thenReturn(null);
    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, DEFAULT_MAX);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void diff_returns404_whenSnapshotBNotFound() {
    when(snapshotService.findByAppId(SNAP_B_APP_ID)).thenReturn(null);
    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, DEFAULT_MAX);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── 200 correct added list ────────────────────────────────────────────────

  @Test
  void diff_200_correctAddedList() {
    // B has entity-c and entity-a that A does not have
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM_ID)).thenReturn(Map.of());
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM_ID)).thenReturn(
      Map.of("entity-c", 1L, "entity-a", 1L)
    );

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, DEFAULT_MAX);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.added()).containsExactly("entity-a", "entity-c");
    assertThat(body.removed()).isEmpty();
    assertThat(body.changed()).isEmpty();
    assertThat(body.unchangedCount()).isZero();
    assertThat(body.totalAdded()).isEqualTo(2);
    assertThat(body.totalRemoved()).isZero();
    assertThat(body.totalChanged()).isZero();
    assertThat(body.truncated()).isFalse();
  }

  // ── 200 correct removed list ──────────────────────────────────────────────

  @Test
  void diff_200_correctRemovedList() {
    // A has entity-b and entity-d that B does not have
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM_ID)).thenReturn(
      Map.of("entity-b", 1L, "entity-d", 1L)
    );
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM_ID)).thenReturn(Map.of());

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, DEFAULT_MAX);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.removed()).containsExactly("entity-b", "entity-d");
    assertThat(body.added()).isEmpty();
    assertThat(body.changed()).isEmpty();
    assertThat(body.unchangedCount()).isZero();
    assertThat(body.totalRemoved()).isEqualTo(2);
    assertThat(body.totalAdded()).isZero();
    assertThat(body.totalChanged()).isZero();
    assertThat(body.truncated()).isFalse();
  }

  // ── 200 correct changed list ──────────────────────────────────────────────

  @Test
  void diff_200_correctChangedList() {
    // entity-x is in both but revision moved from 1 to 3
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM_ID)).thenReturn(
      Map.of("entity-x", 1L)
    );
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM_ID)).thenReturn(
      Map.of("entity-x", 3L)
    );

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, DEFAULT_MAX);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.changed()).hasSize(1);
    DiffEntry entry = body.changed().get(0);
    assertThat(entry.entityAppId()).isEqualTo("entity-x");
    assertThat(entry.revisionA()).isEqualTo(1L);
    assertThat(entry.revisionB()).isEqualTo(3L);
    assertThat(body.added()).isEmpty();
    assertThat(body.removed()).isEmpty();
    assertThat(body.unchangedCount()).isZero();
    assertThat(body.totalChanged()).isEqualTo(1);
    assertThat(body.truncated()).isFalse();
  }

  // ── 200 correct unchangedCount ────────────────────────────────────────────

  @Test
  void diff_200_correctUnchangedCount() {
    // entity-same is in both with identical revision
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM_ID)).thenReturn(
      Map.of("entity-same", 5L)
    );
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM_ID)).thenReturn(
      Map.of("entity-same", 5L)
    );

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, DEFAULT_MAX);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.unchangedCount()).isEqualTo(1);
    assertThat(body.added()).isEmpty();
    assertThat(body.removed()).isEmpty();
    assertThat(body.changed()).isEmpty();
    assertThat(body.truncated()).isFalse();
  }

  // ── 200 lists sorted by entityAppId ──────────────────────────────────────

  @Test
  void diff_200_listsAreSortedByEntityAppId() {
    // A has entity-c (unchanged), entity-e (removed), entity-b (changed rev 1→2)
    // B has entity-c (unchanged), entity-a (added),  entity-b (changed rev 1→2)
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM_ID)).thenReturn(
      Map.of("entity-c", 7L, "entity-e", 3L, "entity-b", 1L)
    );
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM_ID)).thenReturn(
      Map.of("entity-c", 7L, "entity-a", 1L, "entity-b", 2L)
    );

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, DEFAULT_MAX);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    // added: entity-a (only one)
    assertThat(body.added()).containsExactly("entity-a");
    // removed: entity-e (only one)
    assertThat(body.removed()).containsExactly("entity-e");
    // changed: entity-b
    assertThat(body.changed()).hasSize(1);
    assertThat(body.changed().get(0).entityAppId()).isEqualTo("entity-b");
    assertThat(body.changed().get(0).revisionA()).isEqualTo(1L);
    assertThat(body.changed().get(0).revisionB()).isEqualTo(2L);
    // unchanged: entity-c
    assertThat(body.unchangedCount()).isEqualTo(1);
    assertThat(body.truncated()).isFalse();
  }

  // ── 200 metadata fields carried through ──────────────────────────────────

  @Test
  void diff_200_metadataFieldsAreCorrect() {
    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, DEFAULT_MAX);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.snapshotAAppId()).isEqualTo(SNAP_A_APP_ID);
    assertThat(body.snapshotBAppId()).isEqualTo(SNAP_B_APP_ID);
    assertThat(body.snapshotACapturedAtMs()).isEqualTo(SNAP_A_CAPTURED_AT);
    assertThat(body.snapshotBCapturedAtMs()).isEqualTo(SNAP_B_CAPTURED_AT);
  }

  // ── APISIMP-SNAPSHOT-DIFF-UNCAPPED: truncation cap ───────────────────────

  @Test
  void diff_capsLists_whenMaxItemsExceeded() {
    // Build a diff with 12 added + 12 removed + 12 changed entities.
    // maxItems=9 → perListCap = ceil(9/3) = 3, so each list is capped at 3.
    // Total returned: 3+3+3 = 9; truncated = true.
    Map<String, Long> mapA = new HashMap<>();
    Map<String, Long> mapB = new HashMap<>();

    // 12 removed (in A only)
    for (int i = 0; i < 12; i++) {
      mapA.put("removed-" + String.format("%02d", i), 1L);
    }
    // 12 added (in B only)
    for (int i = 0; i < 12; i++) {
      mapB.put("added-" + String.format("%02d", i), 1L);
    }
    // 12 changed (in both with different revisions)
    for (int i = 0; i < 12; i++) {
      String key = "changed-" + String.format("%02d", i);
      mapA.put(key, 1L);
      mapB.put(key, 2L);
    }

    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM_ID)).thenReturn(mapA);
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM_ID)).thenReturn(mapB);

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, 9);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.truncated()).isTrue();

    // Each list capped at ceil(9/3) = 3
    assertThat(body.added()).hasSize(3);
    assertThat(body.removed()).hasSize(3);
    assertThat(body.changed()).hasSize(3);

    // Totals reflect the full uncapped diff
    assertThat(body.totalAdded()).isEqualTo(12);
    assertThat(body.totalRemoved()).isEqualTo(12);
    assertThat(body.totalChanged()).isEqualTo(12);

    // Returned items are the first N alphabetically (sorted before cap)
    assertThat(body.added().get(0)).isEqualTo("added-00");
    assertThat(body.removed().get(0)).isEqualTo("removed-00");
    assertThat(body.changed().get(0).entityAppId()).isEqualTo("changed-00");
  }

  @Test
  void diff_notTruncated_whenDiffFitsWithinMaxItems() {
    // 2 added + 2 removed + 2 changed; maxItems=12 → perListCap=4; no truncation.
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM_ID)).thenReturn(
      Map.of("removed-a", 1L, "removed-b", 1L, "changed-x", 1L, "changed-y", 1L)
    );
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM_ID)).thenReturn(
      Map.of("added-a", 1L, "added-b", 1L, "changed-x", 2L, "changed-y", 2L)
    );

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, 12);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.truncated()).isFalse();
    assertThat(body.added()).hasSize(2);
    assertThat(body.removed()).hasSize(2);
    assertThat(body.changed()).hasSize(2);
    assertThat(body.totalAdded()).isEqualTo(2);
    assertThat(body.totalRemoved()).isEqualTo(2);
    assertThat(body.totalChanged()).isEqualTo(2);
  }

  @Test
  void diff_capsOnlySingleListWhenOnlyThatListExceedsCap() {
    // Only the added list is large (5 entries); maxItems=3 → perListCap=1.
    // Only added is capped; removed and changed are empty so not truncated themselves.
    Map<String, Long> mapB = new HashMap<>();
    for (int i = 0; i < 5; i++) {
      mapB.put("added-" + i, 1L);
    }
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM_ID)).thenReturn(Map.of());
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM_ID)).thenReturn(mapB);

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, 3);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.truncated()).isTrue();
    assertThat(body.added()).hasSize(1); // perListCap = ceil(3/3) = 1
    assertThat(body.removed()).isEmpty();
    assertThat(body.changed()).isEmpty();
    assertThat(body.totalAdded()).isEqualTo(5);
    assertThat(body.totalRemoved()).isZero();
    assertThat(body.totalChanged()).isZero();
  }

  @Test
  void diff_defaultMaxItems_doesNotTruncateSmallDiff() {
    // Default maxItems = 5000; a 3-entry diff (1+1+1) must not truncate.
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM_ID)).thenReturn(
      Map.of("e-removed", 1L, "e-changed", 1L)
    );
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM_ID)).thenReturn(
      Map.of("e-added", 1L, "e-changed", 2L)
    );

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc, SnapshotDiffRest.DEFAULT_MAX_ITEMS);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.truncated()).isFalse();
    assertThat(body.totalAdded()).isEqualTo(body.added().size());
    assertThat(body.totalRemoved()).isEqualTo(body.removed().size());
    assertThat(body.totalChanged()).isEqualTo(body.changed().size());
  }
}
