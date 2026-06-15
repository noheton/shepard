package de.dlr.shepard.v2.containers.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-CONT-NS-COLLAPSE-4 — unit tests for the channel-annotation methods
 * on {@link TimeseriesContainerKindHandler}, replacing the deleted
 * {@code TimeseriesChannelAnnotationRestTest}.
 */
class TimeseriesContainerKindHandlerChannelAnnotationTest {

  static final long CONTAINER_ID = 42L;
  static final String CONTAINER_APP_ID = "01928eaa-0000-7000-8000-000000000042";
  static final String CHANNEL_SHEPARD_ID = "0190abcd-1234-7abc-8def-000000000001";
  static final String ANNOTATION_APP_ID = "01928fff-0000-7000-8000-000000000037";

  @Mock
  AnnotatableTimeseriesService annotatableTimeseriesService;

  @Mock
  TimeseriesContainerService service;

  @Mock
  TimeseriesContainer mockContainer;

  TimeseriesContainerKindHandler handler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new TimeseriesContainerKindHandler();
    handler.service = service;
    handler.annotatableTimeseriesService = annotatableTimeseriesService;
    handler.dao = null;
    handler.userService = null;
    handler.dateHelper = null;
    handler.channelResolver = null;
    handler.dataPointRepository = null;
    handler.annotationDAO = null;

    when(mockContainer.getId()).thenReturn(CONTAINER_ID);
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(mockContainer);
  }

  // ── list annotations ────────────────────────────────────────────────────

  @Test
  void listAnnotations_returnsPresent200_withEmptyList() {
    when(annotatableTimeseriesService.getAnnotationsByChannelShepardId(
            eq(CONTAINER_ID), eq(CHANNEL_SHEPARD_ID)))
        .thenReturn(Collections.emptyList());

    var result = handler.listChannelAnnotations(CONTAINER_APP_ID, CHANNEL_SHEPARD_ID);

    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<SemanticAnnotationIO>) result.get().getEntity();
    assertThat(body).isEmpty();
  }

  @Test
  void listAnnotations_returnsPresent200_withAnnotations() {
    var ann = makeAnnotation(99L, "http://example.org/prop", "Prop",
        "http://example.org/val", "Val");
    when(annotatableTimeseriesService.getAnnotationsByChannelShepardId(
            eq(CONTAINER_ID), eq(CHANNEL_SHEPARD_ID)))
        .thenReturn(List.of(ann));

    var result = handler.listChannelAnnotations(CONTAINER_APP_ID, CHANNEL_SHEPARD_ID);

    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<SemanticAnnotationIO>) result.get().getEntity();
    assertThat(body).hasSize(1);
    assertThat(body.get(0).getPropertyIRI()).isEqualTo("http://example.org/prop");
  }

  // ── create annotation ────────────────────────────────────────────────────

  @Test
  void createAnnotation_returnsPresent201() {
    var ann = makeAnnotation(77L, "http://p.org/pred", "pred", "http://v.org/val", "val");
    when(annotatableTimeseriesService.createAnnotationForChannel(
            eq(CONTAINER_ID), eq(CHANNEL_SHEPARD_ID), any(SemanticAnnotationIO.class)))
        .thenReturn(ann);

    var body = new SemanticAnnotationIO();
    body.setPropertyIRI("http://p.org/pred");
    body.setValueIRI("http://v.org/val");
    var result = handler.createChannelAnnotation(CONTAINER_APP_ID, CHANNEL_SHEPARD_ID, body);

    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(201);
    var entity = (SemanticAnnotationIO) result.get().getEntity();
    assertThat(entity.getId()).isEqualTo(77L);
    verify(annotatableTimeseriesService).createAnnotationForChannel(
        eq(CONTAINER_ID), eq(CHANNEL_SHEPARD_ID), any());
  }

  // ── delete annotation ────────────────────────────────────────────────────

  @Test
  void deleteAnnotation_returnsPresent204() {
    var result = handler.deleteChannelAnnotation(
        CONTAINER_APP_ID, CHANNEL_SHEPARD_ID, ANNOTATION_APP_ID);

    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(204);
    verify(annotatableTimeseriesService).deleteAnnotationForChannel(
        eq(CONTAINER_ID), eq(CHANNEL_SHEPARD_ID), eq(ANNOTATION_APP_ID));
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private static SemanticAnnotation makeAnnotation(
      long id, String propIRI, String propName, String valIRI, String valName) {
    var a = new SemanticAnnotation(id);
    a.setPropertyIRI(propIRI);
    a.setPropertyName(propName);
    a.setValueIRI(valIRI);
    a.setValueName(valName);
    return a;
  }
}
