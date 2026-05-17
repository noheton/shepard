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
 * 200 correct unchangedCount, 200 lists are sorted by entityAppId.
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
    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  // ── 400 self-diff ─────────────────────────────────────────────────────────

  @Test
  void diff_returns400_whenAandBAreSameAppId() {
    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_A_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(400);
  }

  // ── 404 snapshot not found ────────────────────────────────────────────────

  @Test
  void diff_returns404_whenSnapshotANotFound() {
    when(snapshotService.findByAppId(SNAP_A_APP_ID)).thenReturn(null);
    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void diff_returns404_whenSnapshotBNotFound() {
    when(snapshotService.findByAppId(SNAP_B_APP_ID)).thenReturn(null);
    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc);
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

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.added()).containsExactly("entity-a", "entity-c");
    assertThat(body.removed()).isEmpty();
    assertThat(body.changed()).isEmpty();
    assertThat(body.unchangedCount()).isZero();
  }

  // ── 200 correct removed list ──────────────────────────────────────────────

  @Test
  void diff_200_correctRemovedList() {
    // A has entity-b and entity-d that B does not have
    when(snapshotService.getEntryRevisionMap(SNAP_A_OGM_ID)).thenReturn(
      Map.of("entity-b", 1L, "entity-d", 1L)
    );
    when(snapshotService.getEntryRevisionMap(SNAP_B_OGM_ID)).thenReturn(Map.of());

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.removed()).containsExactly("entity-b", "entity-d");
    assertThat(body.added()).isEmpty();
    assertThat(body.changed()).isEmpty();
    assertThat(body.unchangedCount()).isZero();
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

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc);
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

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.unchangedCount()).isEqualTo(1);
    assertThat(body.added()).isEmpty();
    assertThat(body.removed()).isEmpty();
    assertThat(body.changed()).isEmpty();
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

    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc);
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
  }

  // ── 200 metadata fields carried through ──────────────────────────────────

  @Test
  void diff_200_metadataFieldsAreCorrect() {
    Response r = diffRest.diff(SNAP_A_APP_ID, SNAP_B_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);

    SnapshotDiffIO body = (SnapshotDiffIO) r.getEntity();
    assertThat(body.snapshotAAppId()).isEqualTo(SNAP_A_APP_ID);
    assertThat(body.snapshotBAppId()).isEqualTo(SNAP_B_APP_ID);
    assertThat(body.snapshotACapturedAtMs()).isEqualTo(SNAP_A_CAPTURED_AT);
    assertThat(body.snapshotBCapturedAtMs()).isEqualTo(SNAP_B_CAPTURED_AT);
  }
}
