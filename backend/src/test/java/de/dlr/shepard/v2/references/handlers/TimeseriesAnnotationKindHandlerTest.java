package de.dlr.shepard.v2.references.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.timeseries.daos.TimeseriesAnnotationDAO;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-ANNOTATION-SUBRESOURCE-COLLISION — unit tests for the annotation
 * sub-resource methods on {@link TimeseriesReferenceKindHandler}.
 */
class TimeseriesAnnotationKindHandlerTest {

  static final String REF_ID = "ref-app-1";
  static final String ANN_ID = "ann-app-1";

  @Mock
  TimeseriesAnnotationDAO dao;

  TimeseriesReferenceKindHandler handler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new TimeseriesReferenceKindHandler();
    handler.tsAnnotationDAO = dao;
  }

  @Test
  void supportsAnnotations_true() {
    assertThat(handler.supportsAnnotations()).isTrue();
  }

  // ── list ────────────────────────────────────────────────────────────────

  @Test
  void listAnnotations_returnsProjectedMaps() {
    var a = annotation(ANN_ID, 1_000L, null, "spike");
    when(dao.findByTimeseriesReferenceAppId(REF_ID)).thenReturn(List.of(a));

    List<Map<String, Object>> result = handler.listAnnotations(REF_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsEntry("appId", ANN_ID)
      .containsEntry("startNs", 1_000L)
      .containsEntry("label", "spike")
      .containsKey("endNs");
  }

  @Test
  void countAnnotations_delegatesToDao() {
    when(dao.countByTimeseriesReferenceAppId(REF_ID)).thenReturn(7L);
    assertThat(handler.countAnnotations(REF_ID)).isEqualTo(7L);
    verify(dao).countByTimeseriesReferenceAppId(REF_ID);
  }

  @Test
  void listAnnotationsPaged_delegatesToDao() {
    var a = annotation(ANN_ID, 1_000L, null, "spike");
    when(dao.findByTimeseriesReferenceAppId(REF_ID, 6L, 3)).thenReturn(List.of(a));

    List<Map<String, Object>> result = handler.listAnnotations(REF_ID, 6L, 3);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsEntry("startNs", 1_000L);
    verify(dao).findByTimeseriesReferenceAppId(REF_ID, 6L, 3);
  }

  // ── create ──────────────────────────────────────────────────────────────

  @Test
  void createAnnotation_persists() {
    Map<String, Object> body = Map.of("startNs", 1_000L, "label", "anomaly");
    Map<String, Object> result = handler.createAnnotation(REF_ID, body);

    verify(dao).createOrUpdate(any(TimeseriesAnnotation.class));
    verify(dao).linkToReference(eq(REF_ID), any());
    assertThat(result).containsEntry("label", "anomaly")
      .containsEntry("startNs", 1_000L);
  }

  @Test
  void createAnnotation_throwsBadRequest_whenStartNsMissing() {
    assertThatThrownBy(() -> handler.createAnnotation(REF_ID, Map.of("label", "x")))
      .isInstanceOf(BadRequestException.class);
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void createAnnotation_throwsBadRequest_whenLabelBlank() {
    assertThatThrownBy(() -> handler.createAnnotation(REF_ID, Map.of("startNs", 1L, "label", "  ")))
      .isInstanceOf(BadRequestException.class);
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void createAnnotation_throwsBadRequest_whenLabelMissing() {
    assertThatThrownBy(() -> handler.createAnnotation(REF_ID, Map.of("startNs", 1L)))
      .isInstanceOf(BadRequestException.class);
    verify(dao, never()).createOrUpdate(any());
  }

  // ── get ─────────────────────────────────────────────────────────────────

  @Test
  void getAnnotation_returnsMap() {
    var a = annotation(ANN_ID, 1_000L, 2_000L, "interval");
    when(dao.findByAppId(ANN_ID)).thenReturn(a);

    Map<String, Object> result = handler.getAnnotation(REF_ID, ANN_ID);
    assertThat(result).containsEntry("endNs", 2_000L)
      .containsEntry("label", "interval");
  }

  @Test
  void getAnnotation_throwsNotFound_whenMissing() {
    when(dao.findByAppId(ANN_ID)).thenReturn(null);
    assertThatThrownBy(() -> handler.getAnnotation(REF_ID, ANN_ID))
      .isInstanceOf(NotFoundException.class);
  }

  // ── patch ───────────────────────────────────────────────────────────────

  @Test
  void patchAnnotation_updatesLabel() {
    var a = annotation(ANN_ID, 1_000L, null, "old");
    when(dao.findByAppId(ANN_ID)).thenReturn(a);

    Map<String, Object> result = handler.patchAnnotation(REF_ID, ANN_ID, Map.of("label", "new"));

    assertThat(result).containsEntry("label", "new");
    verify(dao).createOrUpdate(a);
  }

  @Test
  void patchAnnotation_throwsBadRequest_whenLabelBlank() {
    var a = annotation(ANN_ID, 1_000L, null, "old");
    when(dao.findByAppId(ANN_ID)).thenReturn(a);

    assertThatThrownBy(() -> handler.patchAnnotation(REF_ID, ANN_ID, Map.of("label", "  ")))
      .isInstanceOf(BadRequestException.class);
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void patchAnnotation_throwsNotFound_whenMissing() {
    when(dao.findByAppId(ANN_ID)).thenReturn(null);
    assertThatThrownBy(() -> handler.patchAnnotation(REF_ID, ANN_ID, Map.of("label", "x")))
      .isInstanceOf(NotFoundException.class);
  }

  // ── delete ──────────────────────────────────────────────────────────────

  @Test
  void deleteAnnotation_unlinksAndDeletes() {
    var a = annotation(ANN_ID, 1_000L, null, "to-delete");
    when(dao.findByAppId(ANN_ID)).thenReturn(a);

    handler.deleteAnnotation(REF_ID, ANN_ID);
    verify(dao).unlinkAndDelete(REF_ID, a);
  }

  @Test
  void deleteAnnotation_throwsNotFound_whenMissing() {
    when(dao.findByAppId(ANN_ID)).thenReturn(null);
    assertThatThrownBy(() -> handler.deleteAnnotation(REF_ID, ANN_ID))
      .isInstanceOf(NotFoundException.class);
    verify(dao, never()).unlinkAndDelete(any(), any());
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private static TimeseriesAnnotation annotation(String appId, long startNs, Long endNs, String label) {
    var a = new TimeseriesAnnotation();
    a.setAppId(appId);
    a.setStartNs(startNs);
    a.setEndNs(endNs);
    a.setLabel(label);
    return a;
  }
}
