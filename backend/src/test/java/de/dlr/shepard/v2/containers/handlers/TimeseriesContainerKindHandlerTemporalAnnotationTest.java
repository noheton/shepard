package de.dlr.shepard.v2.containers.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.timeseries.daos.TimeseriesAnnotationDAO;
import de.dlr.shepard.v2.timeseries.io.TimeseriesAnnotationIO;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-CONT-NS-COLLAPSE-4 — unit tests for the temporal-annotation methods
 * on {@link TimeseriesContainerKindHandler}, replacing the deleted
 * {@code TimeseriesContainerTemporalAnnotationRestTest}.
 */
class TimeseriesContainerKindHandlerTemporalAnnotationTest {

  static final long CONTAINER_ID = 42L;
  static final String CONTAINER_APP_ID = "01928eaa-0000-7000-8000-000000000042";
  static final String ANN_APP_ID = "ann-appid-1";

  @Mock
  TimeseriesAnnotationDAO annotationDAO;

  @Mock
  TimeseriesContainerService service;

  @Mock
  TimeseriesContainer container;

  TimeseriesContainerKindHandler handler;
  TimeseriesAnnotation annotation;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new TimeseriesContainerKindHandler();
    handler.service = service;
    handler.annotationDAO = annotationDAO;
    handler.dao = null;
    handler.userService = null;
    handler.dateHelper = null;
    handler.channelResolver = null;
    handler.dataPointRepository = null;
    handler.annotatableTimeseriesService = null;

    annotation = new TimeseriesAnnotation(1L);
    annotation.setAppId(ANN_APP_ID);
    annotation.setStartNs(1_000_000_000L);
    annotation.setEndNs(2_000_000_000L);
    annotation.setLabel("anomaly");

    when(container.getId()).thenReturn(CONTAINER_ID);
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);
  }

  // ── list ──────────────────────────────────────────────────────────────────

  @Test
  void list_returnsPresent200_withEmptyArray() {
    when(annotationDAO.findByContainerId(CONTAINER_ID)).thenReturn(List.of());
    var result = handler.listTemporalAnnotations(CONTAINER_APP_ID);
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(200);
    assertThat((List<?>) result.get().getEntity()).isEmpty();
  }

  @Test
  void list_returnsPresent200_withAnnotations() {
    when(annotationDAO.findByContainerId(CONTAINER_ID)).thenReturn(List.of(annotation));
    var result = handler.listTemporalAnnotations(CONTAINER_APP_ID);
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(200);
    List<?> rows = (List<?>) result.get().getEntity();
    assertThat(rows).hasSize(1);
    assertThat(((TimeseriesAnnotationIO) rows.get(0)).getLabel()).isEqualTo("anomaly");
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void create_returnsPresent400_whenStartNsNull() {
    TimeseriesAnnotationIO body = new TimeseriesAnnotationIO();
    body.setLabel("event");
    var result = handler.createTemporalAnnotation(CONTAINER_APP_ID, body);
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_returnsPresent400_whenLabelBlank() {
    TimeseriesAnnotationIO body = new TimeseriesAnnotationIO();
    body.setStartNs(1_000_000_000L);
    body.setLabel("  ");
    var result = handler.createTemporalAnnotation(CONTAINER_APP_ID, body);
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_returnsPresent201_andLinksAnnotation() {
    TimeseriesAnnotationIO body = new TimeseriesAnnotationIO();
    body.setStartNs(1_000_000_000L);
    body.setEndNs(2_000_000_000L);
    body.setLabel("spike");
    var result = handler.createTemporalAnnotation(CONTAINER_APP_ID, body);
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(201);
    verify(annotationDAO).createOrUpdate(any());
    verify(annotationDAO).linkToContainer(eq(CONTAINER_ID), anyString());
    TimeseriesAnnotationIO out = (TimeseriesAnnotationIO) result.get().getEntity();
    assertThat(out.getLabel()).isEqualTo("spike");
    assertThat(out.getStartNs()).isEqualTo(1_000_000_000L);
  }

  // ── get by appId ──────────────────────────────────────────────────────────

  @Test
  void get_returnsPresent404_whenAnnotationNotFound() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(null);
    var result = handler.getTemporalAnnotation(CONTAINER_APP_ID, ANN_APP_ID);
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(404);
  }

  @Test
  void get_returnsPresent200_whenFound() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(annotation);
    var result = handler.getTemporalAnnotation(CONTAINER_APP_ID, ANN_APP_ID);
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(200);
    assertThat(((TimeseriesAnnotationIO) result.get().getEntity()).getAppId()).isEqualTo(ANN_APP_ID);
  }

  // ── update ────────────────────────────────────────────────────────────────

  @Test
  void update_returnsPresent404_whenAnnotationNotFound() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(null);
    var result = handler.updateTemporalAnnotation(
        CONTAINER_APP_ID, ANN_APP_ID, new TimeseriesAnnotationIO());
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(404);
  }

  @Test
  void update_returnsPresent400_whenLabelIsBlank() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(annotation);
    TimeseriesAnnotationIO body = new TimeseriesAnnotationIO();
    body.setLabel("  ");
    var result = handler.updateTemporalAnnotation(CONTAINER_APP_ID, ANN_APP_ID, body);
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void update_returnsPresent200_patchesLabel() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(annotation);
    TimeseriesAnnotationIO body = new TimeseriesAnnotationIO();
    body.setLabel("relabelled");
    var result = handler.updateTemporalAnnotation(CONTAINER_APP_ID, ANN_APP_ID, body);
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(200);
    assertThat(((TimeseriesAnnotationIO) result.get().getEntity()).getLabel()).isEqualTo("relabelled");
    assertThat(((TimeseriesAnnotationIO) result.get().getEntity()).getStartNs())
        .isEqualTo(1_000_000_000L);
  }

  // ── delete ────────────────────────────────────────────────────────────────

  @Test
  void delete_returnsPresent404_whenAnnotationNotFound() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(null);
    var result = handler.deleteTemporalAnnotation(CONTAINER_APP_ID, ANN_APP_ID);
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(404);
  }

  @Test
  void delete_returnsPresent204_andUnlinks() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(annotation);
    var result = handler.deleteTemporalAnnotation(CONTAINER_APP_ID, ANN_APP_ID);
    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(204);
    verify(annotationDAO).unlinkAndDeleteFromContainer(eq(CONTAINER_ID), eq(annotation));
  }
}
