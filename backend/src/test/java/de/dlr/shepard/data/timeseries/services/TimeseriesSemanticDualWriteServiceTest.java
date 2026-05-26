package de.dlr.shepard.data.timeseries.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO;
import de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * TS-SEMANTIC-01 — unit tests for {@link TimeseriesSemanticDualWriteService}.
 *
 * <p>Eight required scenarios:
 * <ol>
 *   <li>Upsert with all 5 fields → 5 SemanticAnnotation nodes written.</li>
 *   <li>Upsert with only measurement + field → 2 nodes created, no null-field nodes.</li>
 *   <li>Upsert idempotent — second call with same channel creates no duplicates.</li>
 *   <li>Neo4j write failure → WARN logged, call completes without propagating exception.</li>
 *   <li>Empty measurement (blank string) → not written as annotation.</li>
 *   <li>shepardId is set as appId on the AnnotatableTimeseries subject node.</li>
 *   <li>Each annotation has sourceMode = "ai".</li>
 *   <li>Annotation predicate for measurement is {@value Constants#TS_PREDICATE_MEASUREMENT}.</li>
 * </ol>
 */
@QuarkusComponentTest
public class TimeseriesSemanticDualWriteServiceTest {

  @InjectMock
  AnnotatableTimeseriesDAO annotatableTimeseriesDAO;

  @Inject
  TimeseriesSemanticDualWriteService service;

  // ─── Helpers ────────────────────────────────────────────────────────────────

  private static final long CONTAINER_ID = 10L;
  private static final int TS_ID = 42;
  private static final String SHEPARD_UUID = "01900000-dead-7000-beef-000000000001";

  private AnnotatableTimeseries freshSubject() {
    return new AnnotatableTimeseries(CONTAINER_ID, TS_ID, new ArrayList<>());
  }

  /** Stub createOrUpdate to return the same entity it receives (mirrors Neo4j save semantics). */
  private void stubCreateOrUpdate() {
    when(annotatableTimeseriesDAO.createOrUpdate(any()))
      .thenAnswer(inv -> inv.getArgument(0));
  }

  // ─── Test 1: all 5 fields → 5 annotations ──────────────────────────────────

  @Test
  public void allFiveFields_writeFiveAnnotations() {
    when(annotatableTimeseriesDAO.findByTimeseries(CONTAINER_ID, TS_ID))
      .thenReturn(Optional.empty());
    stubCreateOrUpdate();

    service.dualWriteChannelMetadata(
      CONTAINER_ID, TS_ID, SHEPARD_UUID,
      "vibration", "rpm", "sensor-01", "zone-A", "TB-001"
    );

    ArgumentCaptor<AnnotatableTimeseries> captor = ArgumentCaptor.forClass(AnnotatableTimeseries.class);
    verify(annotatableTimeseriesDAO, times(1)).createOrUpdate(captor.capture());

    AnnotatableTimeseries saved = captor.getValue();
    assertEquals(5, saved.getAnnotations().size(), "Expected 5 annotation nodes for 5 non-blank fields");
  }

  // ─── Test 2: only measurement + field → 2 annotations, no null-field nodes ─

  @Test
  public void onlyMeasurementAndField_writeTwoAnnotations() {
    when(annotatableTimeseriesDAO.findByTimeseries(CONTAINER_ID, TS_ID))
      .thenReturn(Optional.empty());
    stubCreateOrUpdate();

    // device, location, symbolicName are blank / null
    service.dualWriteChannelMetadata(
      CONTAINER_ID, TS_ID, SHEPARD_UUID,
      "temperature", "celsius", null, "", null
    );

    ArgumentCaptor<AnnotatableTimeseries> captor = ArgumentCaptor.forClass(AnnotatableTimeseries.class);
    verify(annotatableTimeseriesDAO, times(1)).createOrUpdate(captor.capture());

    AnnotatableTimeseries saved = captor.getValue();
    assertEquals(2, saved.getAnnotations().size(), "Expected exactly 2 annotation nodes");

    List<String> predicates = saved.getAnnotations().stream()
      .map(SemanticAnnotation::getPropertyIRI)
      .toList();
    org.junit.jupiter.api.Assertions.assertTrue(
      predicates.contains(Constants.TS_PREDICATE_MEASUREMENT), "Must have measurement predicate");
    org.junit.jupiter.api.Assertions.assertTrue(
      predicates.contains(Constants.TS_PREDICATE_FIELD), "Must have field predicate");
    org.junit.jupiter.api.Assertions.assertFalse(
      predicates.contains(Constants.TS_PREDICATE_DEVICE), "Must NOT have device predicate");
  }

  // ─── Test 3: idempotent — second call with same channel, stale annotations deleted ──

  @Test
  public void secondCall_staleAnnotationsDeletedAndRecreated() {
    // First call: subject doesn't exist yet → created
    when(annotatableTimeseriesDAO.findByTimeseries(CONTAINER_ID, TS_ID))
      .thenReturn(Optional.empty());
    stubCreateOrUpdate();

    service.dualWriteChannelMetadata(
      CONTAINER_ID, TS_ID, SHEPARD_UUID, "pressure", "bar", null, null, null
    );
    verify(annotatableTimeseriesDAO, times(1)).createOrUpdate(any());

    // Second call: simulate existing subject with a stale ts-channel-metadata annotation
    SemanticAnnotation stale = new SemanticAnnotation();
    stale.setId(99L);
    stale.setPropertyIRI(Constants.TS_PREDICATE_MEASUREMENT);
    stale.setSource(Constants.ANNOTATION_SOURCE_TS_CHANNEL_METADATA);

    AnnotatableTimeseries existingSubject = new AnnotatableTimeseries(CONTAINER_ID, TS_ID, new ArrayList<>());
    existingSubject.setAppId(SHEPARD_UUID);
    existingSubject.getAnnotations().add(stale);

    // First findByTimeseries call returns the existing subject (triggers stale purge);
    // second call (after deletion) returns a clean version.
    AnnotatableTimeseries cleanSubject = new AnnotatableTimeseries(CONTAINER_ID, TS_ID, new ArrayList<>());
    cleanSubject.setAppId(SHEPARD_UUID);

    when(annotatableTimeseriesDAO.findByTimeseries(CONTAINER_ID, TS_ID))
      .thenReturn(Optional.of(existingSubject))
      .thenReturn(Optional.of(cleanSubject));

    service.dualWriteChannelMetadata(
      CONTAINER_ID, TS_ID, SHEPARD_UUID, "pressure", "bar", null, null, null
    );

    // Stale annotation must be deleted
    verify(annotatableTimeseriesDAO).deleteAnnotation(99L);
    // createOrUpdate called again for the re-written set (called twice total — once per dualWrite call)
    verify(annotatableTimeseriesDAO, times(2)).createOrUpdate(any());
  }

  // ─── Test 4: Neo4j write failure → no exception propagated ────────────────

  @Test
  public void neo4jWriteFailure_completesWithoutException() {
    when(annotatableTimeseriesDAO.findByTimeseries(CONTAINER_ID, TS_ID))
      .thenReturn(Optional.empty());
    when(annotatableTimeseriesDAO.createOrUpdate(any()))
      .thenThrow(new RuntimeException("simulated Neo4j connection failure"));

    // Must complete without throwing — best-effort contract
    service.dualWriteChannelMetadata(
      CONTAINER_ID, TS_ID, SHEPARD_UUID, "temp", "K", null, null, null
    );
    // Test passes if no exception escapes
  }

  // ─── Test 5: blank measurement string → not written ────────────────────────

  @Test
  public void blankMeasurement_notWrittenAsAnnotation() {
    when(annotatableTimeseriesDAO.findByTimeseries(CONTAINER_ID, TS_ID))
      .thenReturn(Optional.empty());
    stubCreateOrUpdate();

    service.dualWriteChannelMetadata(
      CONTAINER_ID, TS_ID, SHEPARD_UUID,
      "   ",   // blank measurement — must be skipped
      "kPa",   // non-blank field
      null, null, null
    );

    ArgumentCaptor<AnnotatableTimeseries> captor = ArgumentCaptor.forClass(AnnotatableTimeseries.class);
    verify(annotatableTimeseriesDAO, times(1)).createOrUpdate(captor.capture());

    List<String> predicates = captor.getValue().getAnnotations().stream()
      .map(SemanticAnnotation::getPropertyIRI)
      .toList();
    org.junit.jupiter.api.Assertions.assertFalse(
      predicates.contains(Constants.TS_PREDICATE_MEASUREMENT),
      "Blank measurement must NOT produce an annotation node");
    assertEquals(1, predicates.size(), "Only 'field' annotation expected");
  }

  // ─── Test 6: shepardId set as appId on AnnotatableTimeseries subject node ──

  @Test
  public void shepardId_setAsAppIdOnSubjectNode() {
    when(annotatableTimeseriesDAO.findByTimeseries(CONTAINER_ID, TS_ID))
      .thenReturn(Optional.empty());
    stubCreateOrUpdate();

    service.dualWriteChannelMetadata(
      CONTAINER_ID, TS_ID, SHEPARD_UUID, "rpm", "hz", null, null, null
    );

    ArgumentCaptor<AnnotatableTimeseries> captor = ArgumentCaptor.forClass(AnnotatableTimeseries.class);
    verify(annotatableTimeseriesDAO).createOrUpdate(captor.capture());

    assertEquals(
      SHEPARD_UUID,
      captor.getValue().getAppId(),
      "AnnotatableTimeseries.appId must equal the timeseries shepardId UUID"
    );
  }

  // ─── Test 7: each annotation has sourceMode = "ai" ─────────────────────────

  @Test
  public void annotations_haveSourceModeAi() {
    when(annotatableTimeseriesDAO.findByTimeseries(CONTAINER_ID, TS_ID))
      .thenReturn(Optional.empty());
    stubCreateOrUpdate();

    service.dualWriteChannelMetadata(
      CONTAINER_ID, TS_ID, SHEPARD_UUID, "vibration", "g", "sensor-X", null, null
    );

    ArgumentCaptor<AnnotatableTimeseries> captor = ArgumentCaptor.forClass(AnnotatableTimeseries.class);
    verify(annotatableTimeseriesDAO).createOrUpdate(captor.capture());

    for (SemanticAnnotation ann : captor.getValue().getAnnotations()) {
      assertEquals("ai", ann.getSourceMode(), "sourceMode must be 'ai' per TS-SEMANTIC-01 spec");
    }
  }

  // ─── Test 8: measurement annotation uses correct predicate IRI ─────────────

  @Test
  public void measurementAnnotation_hasCorrectPredicateIri() {
    when(annotatableTimeseriesDAO.findByTimeseries(CONTAINER_ID, TS_ID))
      .thenReturn(Optional.empty());
    stubCreateOrUpdate();

    service.dualWriteChannelMetadata(
      CONTAINER_ID, TS_ID, SHEPARD_UUID, "thrust", "N", null, null, null
    );

    ArgumentCaptor<AnnotatableTimeseries> captor = ArgumentCaptor.forClass(AnnotatableTimeseries.class);
    verify(annotatableTimeseriesDAO).createOrUpdate(captor.capture());

    SemanticAnnotation measurementAnn = captor.getValue().getAnnotations().stream()
      .filter(a -> Constants.TS_PREDICATE_MEASUREMENT.equals(a.getPropertyIRI()))
      .findFirst()
      .orElse(null);

    assertNotNull(measurementAnn, "Annotation with predicate TS_PREDICATE_MEASUREMENT must exist");
    assertEquals(Constants.TS_PREDICATE_MEASUREMENT, measurementAnn.getPropertyIRI());
    assertEquals("measurement", measurementAnn.getPropertyName(), "localName must be 'measurement'");
    assertEquals("thrust", measurementAnn.getValueName(), "valueName must equal the measurement string");
    assertEquals(Constants.ANNOTATION_SOURCE_TS_CHANNEL_METADATA, measurementAnn.getSource());
    assertEquals(Constants.SUBJECT_KIND_ANNOTATABLE_TIMESERIES, measurementAnn.getSubjectKind());
    assertEquals(SHEPARD_UUID, measurementAnn.getSubjectAppId());
    assertNull(measurementAnn.getVocabularyId(), "vocabularyId must be null — no controlled vocabulary");
    assertNull(measurementAnn.getConfidence());
  }

  // ─── Bonus test: null shepardId → skipped (no NPE) ─────────────────────────

  @Test
  public void nullShepardId_skippedGracefully() {
    service.dualWriteChannelMetadata(
      CONTAINER_ID, TS_ID, null, "rpm", "hz", null, null, null
    );
    verify(annotatableTimeseriesDAO, never()).findByTimeseries(anyLong(), any(Integer.class));
    verify(annotatableTimeseriesDAO, never()).createOrUpdate(any());
  }

  // ─── Static helper: localName ───────────────────────────────────────────────

  @Test
  public void localName_extractsCorrectly() {
    assertEquals("measurement", TimeseriesSemanticDualWriteService.localName("urn:shepard:channel:measurement"));
    assertEquals("field",        TimeseriesSemanticDualWriteService.localName("urn:shepard:channel:field"));
    assertEquals("symbolicName", TimeseriesSemanticDualWriteService.localName("urn:shepard:channel:symbolicName"));
    assertEquals("foo",          TimeseriesSemanticDualWriteService.localName("foo"));
    assertEquals("",             TimeseriesSemanticDualWriteService.localName(null));
  }
}
