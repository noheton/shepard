package de.dlr.shepard.v2.video.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.video.daos.VideoAnnotationDAO;
import de.dlr.shepard.v2.video.model.VideoAnnotation;
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
 * sub-resource methods on {@link VideoStreamReferenceKindHandler}.
 */
class VideoAnnotationKindHandlerTest {

  static final String REF_ID = "ref-vid-1";
  static final String ANN_ID = "ann-vid-1";

  @Mock
  VideoAnnotationDAO dao;

  VideoStreamReferenceKindHandler handler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new VideoStreamReferenceKindHandler();
    handler.videoAnnotationDAO = dao;
  }

  @Test
  void supportsAnnotations_true() {
    assertThat(handler.supportsAnnotations()).isTrue();
  }

  // ── list ────────────────────────────────────────────────────────────────

  @Test
  void listAnnotations_returnsProjectedMaps() {
    var a = annotation(ANN_ID, 0.0, 5.0, "ignition");
    when(dao.findByVideoReferenceAppId(REF_ID)).thenReturn(List.of(a));

    List<Map<String, Object>> result = handler.listAnnotations(REF_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0))
      .containsEntry("appId", ANN_ID)
      .containsEntry("startSeconds", 0.0)
      .containsEntry("endSeconds", 5.0)
      .containsEntry("label", "ignition");
  }

  @Test
  void countAnnotations_delegatesToDao() {
    when(dao.countByVideoReferenceAppId(REF_ID)).thenReturn(5L);
    assertThat(handler.countAnnotations(REF_ID)).isEqualTo(5L);
    verify(dao).countByVideoReferenceAppId(REF_ID);
  }

  @Test
  void listAnnotationsPaged_delegatesToDao() {
    var a = annotation(ANN_ID, 0.0, 5.0, "ignition");
    when(dao.findByVideoReferenceAppId(REF_ID, 6, 3)).thenReturn(List.of(a));

    List<Map<String, Object>> result = handler.listAnnotations(REF_ID, 6, 3);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsEntry("startSeconds", 0.0);
    verify(dao).findByVideoReferenceAppId(REF_ID, 6, 3);
  }

  // ── create ──────────────────────────────────────────────────────────────

  @Test
  void createAnnotation_persists() {
    Map<String, Object> body = Map.of("startSeconds", 0.0, "endSeconds", 5.0, "label", "ignition");
    Map<String, Object> result = handler.createAnnotation(REF_ID, body);

    verify(dao).createOrUpdate(any(VideoAnnotation.class));
    verify(dao).linkToReference(eq(REF_ID), any());
    assertThat(result).containsEntry("label", "ignition")
      .containsEntry("startSeconds", 0.0);
  }

  @Test
  void createAnnotation_throwsBadRequest_whenStartSecondsMissing() {
    assertThatThrownBy(() -> handler.createAnnotation(REF_ID, Map.of("label", "x")))
      .isInstanceOf(BadRequestException.class);
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void createAnnotation_throwsBadRequest_whenLabelBlank() {
    assertThatThrownBy(() -> handler.createAnnotation(REF_ID, Map.of("startSeconds", 0.0, "label", "  ")))
      .isInstanceOf(BadRequestException.class);
    verify(dao, never()).createOrUpdate(any());
  }

  @Test
  void createAnnotation_throwsBadRequest_whenLabelMissing() {
    assertThatThrownBy(() -> handler.createAnnotation(REF_ID, Map.of("startSeconds", 0.0)))
      .isInstanceOf(BadRequestException.class);
    verify(dao, never()).createOrUpdate(any());
  }

  // ── get ─────────────────────────────────────────────────────────────────

  @Test
  void getAnnotation_returnsMap() {
    var a = annotation(ANN_ID, 5.0, 35.0, "burn");
    when(dao.findByAppId(ANN_ID)).thenReturn(a);

    Map<String, Object> result = handler.getAnnotation(REF_ID, ANN_ID);
    assertThat(result).containsEntry("endSeconds", 35.0)
      .containsEntry("label", "burn");
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
    var a = annotation(ANN_ID, 0.0, 5.0, "old-label");
    when(dao.findByAppId(ANN_ID)).thenReturn(a);

    Map<String, Object> result = handler.patchAnnotation(REF_ID, ANN_ID, Map.of("label", "new-label"));

    assertThat(result).containsEntry("label", "new-label");
    verify(dao).createOrUpdate(a);
  }

  @Test
  void patchAnnotation_throwsBadRequest_whenLabelBlank() {
    var a = annotation(ANN_ID, 0.0, 5.0, "old-label");
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
    var a = annotation(ANN_ID, 35.0, 50.0, "cooldown");
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

  private static VideoAnnotation annotation(String appId, double startSeconds, Double endSeconds, String label) {
    var a = new VideoAnnotation();
    a.setAppId(appId);
    a.setStartSeconds(startSeconds);
    a.setEndSeconds(endSeconds);
    a.setLabel(label);
    return a;
  }
}
