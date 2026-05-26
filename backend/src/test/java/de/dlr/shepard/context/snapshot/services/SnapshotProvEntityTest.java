package de.dlr.shepard.context.snapshot.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.snapshot.daos.SnapshotDAO;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import de.dlr.shepard.v2.annotations.daos.SemanticAnnotationV2DAO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * PROV1i — unit tests for the automatic {@code prov:Entity} typing emitted by
 * {@link SnapshotService} on snapshot creation.
 *
 * <p>Tests verify:
 * <ol>
 *   <li>{@code createSnapshot} emits exactly one {@link SemanticAnnotation} with
 *       the correct {@code rdf:type} / {@code prov:Entity} shape.</li>
 *   <li>If the annotation write fails, the snapshot is still returned to the
 *       caller (best-effort posture).</li>
 *   <li>{@code emitProvEntityTyping} is a no-op for null / no-appId snapshots.</li>
 * </ol>
 */
class SnapshotProvEntityTest {

  static final String COLL_APP_ID = "01900000-0000-7000-8000-000000000010";
  static final String SNAP_APP_ID = "01900000-0000-7000-8000-000000000020";
  static final String CALLER = "alice";

  @Mock
  SnapshotDAO snapshotDAO;

  @Mock
  CollectionDAO collectionDAO;

  @Mock
  SemanticAnnotationV2DAO semanticAnnotationV2DAO;

  SnapshotService service;

  Collection collection;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new SnapshotService();
    service.snapshotDAO = snapshotDAO;
    service.collectionDAO = collectionDAO;
    service.semanticAnnotationV2DAO = semanticAnnotationV2DAO;

    collection = new Collection();
    collection.setId(10L);
    collection.setAppId(COLL_APP_ID);

    when(collectionDAO.findByQuery(anyString(), any())).thenReturn(List.of(collection));
    when(snapshotDAO.walkCollectionSubtree(COLL_APP_ID)).thenReturn(List.of());
    when(snapshotDAO.createOrUpdate(any(Snapshot.class))).thenAnswer(inv -> {
      Snapshot s = inv.getArgument(0);
      if (s.getAppId() == null) s.setAppId(SNAP_APP_ID);
      return s;
    });
    when(semanticAnnotationV2DAO.createOrUpdate(any(SemanticAnnotation.class)))
      .thenAnswer(inv -> inv.getArgument(0));
  }

  // ── createSnapshot_emitsProvEntityTyping ────────────────────────────────────

  /**
   * PROV1i — the primary happy-path test. After {@code createSnapshot} returns,
   * exactly one {@code SemanticAnnotation} must have been persisted with the
   * correct PROV-O shape.
   */
  @Test
  void createSnapshot_emitsProvEntityTyping() {
    Snapshot result = service.createSnapshot(COLL_APP_ID, "v1.0", "test", CALLER);

    // snapshot itself is returned correctly
    assertThat(result).isNotNull();
    assertThat(result.getAppId()).isEqualTo(SNAP_APP_ID);

    // exactly one annotation was written
    ArgumentCaptor<SemanticAnnotation> captor = ArgumentCaptor.forClass(SemanticAnnotation.class);
    verify(semanticAnnotationV2DAO, times(1)).createOrUpdate(captor.capture());

    SemanticAnnotation typing = captor.getValue();
    assertThat(typing.getSubjectAppId()).isEqualTo(SNAP_APP_ID);
    assertThat(typing.getSubjectKind()).isEqualTo("Snapshot");
    assertThat(typing.getPropertyIRI()).isEqualTo(SnapshotService.RDF_TYPE_IRI);
    assertThat(typing.getPropertyName()).isEqualTo(SnapshotService.RDF_TYPE_LABEL);
    assertThat(typing.getValueIRI()).isEqualTo(SnapshotService.PROV_ENTITY_IRI);
    assertThat(typing.getValueName()).isEqualTo(SnapshotService.PROV_ENTITY_LABEL);
    assertThat(typing.getSource()).isEqualTo(SnapshotService.SOURCE_SYSTEM);
    assertThat(typing.getSourceMode()).isEqualTo("ai");
    assertThat(typing.getConfidence()).isEqualTo(1.0);
    assertThat(typing.getAppId()).isNotNull(); // UUID v7 minted
  }

  // ── provEntityTypingIsBestEffort_doesNotFailSnapshot ────────────────────────

  /**
   * PROV1i — if the annotation DAO throws, the snapshot creation must still
   * succeed and the snapshot must be returned to the caller.
   *
   * <p>This verifies the best-effort posture: snapshot integrity trumps the
   * optional PROV-O typing decoration.
   */
  @Test
  void provEntityTypingIsBestEffort_doesNotFailSnapshot() {
    doThrow(new RuntimeException("Neo4j unavailable"))
      .when(semanticAnnotationV2DAO).createOrUpdate(any());

    // Must NOT throw — snapshot is returned despite DAO failure
    Snapshot result = service.createSnapshot(COLL_APP_ID, "v1.0", "test", CALLER);

    assertThat(result).isNotNull();
    assertThat(result.getAppId()).isEqualTo(SNAP_APP_ID);

    // The DAO was called (attempt was made), but failure was swallowed
    verify(semanticAnnotationV2DAO, times(1)).createOrUpdate(any());
  }

  // ── emitProvEntityTyping_nullSnapshot ───────────────────────────────────────

  /**
   * PROV1i — {@code emitProvEntityTyping(null)} must be a no-op (no call to the DAO).
   */
  @Test
  void emitProvEntityTyping_nullSnapshot_isNoOp() {
    service.emitProvEntityTyping(null);
    verify(semanticAnnotationV2DAO, never()).createOrUpdate(any());
  }

  /**
   * PROV1i — {@code emitProvEntityTyping} with a snapshot whose {@code appId} is
   * {@code null} must be a no-op (no call to the DAO). This covers the edge case
   * of a snapshot returned by the DAO before the L2a backfill has set the appId.
   */
  @Test
  void emitProvEntityTyping_nullAppId_isNoOp() {
    Snapshot noId = new Snapshot();
    // appId is null — not yet backfilled
    service.emitProvEntityTyping(noId);
    verify(semanticAnnotationV2DAO, never()).createOrUpdate(any());
  }

  // ── emitProvEntityTyping_correctIRIs ────────────────────────────────────────

  /**
   * PROV1i — smoke test on the IRI constants to catch copy-paste mistakes.
   */
  @Test
  void snapshotService_provIriConstants_areCorrect() {
    assertThat(SnapshotService.RDF_TYPE_IRI)
      .isEqualTo("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
    assertThat(SnapshotService.PROV_ENTITY_IRI)
      .isEqualTo("http://www.w3.org/ns/prov#Entity");
  }
}
