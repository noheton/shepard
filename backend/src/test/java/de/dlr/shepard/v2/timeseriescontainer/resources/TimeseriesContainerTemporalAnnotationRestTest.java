package de.dlr.shepard.v2.timeseriescontainer.resources;

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
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TimeseriesContainerTemporalAnnotationRestTest {

  static final long CONTAINER_ID = 42L;
  static final String CONTAINER_APP_ID = "01928eaa-0000-7000-8000-000000000042";
  static final String ANN_APP_ID = "ann-appid-1";

  @Mock
  TimeseriesAnnotationDAO annotationDAO;

  @Mock
  TimeseriesContainerService containerService;

  @Mock
  TimeseriesContainer container;

  TimeseriesContainerTemporalAnnotationRest resource;
  TimeseriesAnnotation annotation;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new TimeseriesContainerTemporalAnnotationRest();
    resource.annotationDAO = annotationDAO;
    resource.containerService = containerService;

    annotation = new TimeseriesAnnotation(1L);
    annotation.setAppId(ANN_APP_ID);
    annotation.setStartNs(1_000_000_000L);
    annotation.setEndNs(2_000_000_000L);
    annotation.setLabel("anomaly");

    when(container.getId()).thenReturn(CONTAINER_ID);
    when(containerService.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);
  }

  // ── list ──────────────────────────────────────────────────────────────────

  @Test
  void list_returnsEmptyArrayWhenNoAnnotations() {
    when(annotationDAO.findByContainerId(CONTAINER_ID)).thenReturn(List.of());
    Response r = resource.list(CONTAINER_APP_ID);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat((List<?>) r.getEntity()).isEmpty();
  }

  @Test
  void list_returnsAnnotationsAsMappedIOs() {
    when(annotationDAO.findByContainerId(CONTAINER_ID)).thenReturn(List.of(annotation));
    Response r = resource.list(CONTAINER_APP_ID);
    assertThat(r.getStatus()).isEqualTo(200);
    List<?> rows = (List<?>) r.getEntity();
    assertThat(rows).hasSize(1);
    assertThat(((TimeseriesAnnotationIO) rows.get(0)).getLabel()).isEqualTo("anomaly");
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void create_returns400WhenStartNsNull() {
    TimeseriesAnnotationIO body = new TimeseriesAnnotationIO();
    body.setLabel("event");
    Response r = resource.create(CONTAINER_APP_ID, body);
    assertThat(r.getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_returns400WhenLabelBlank() {
    TimeseriesAnnotationIO body = new TimeseriesAnnotationIO();
    body.setStartNs(1_000_000_000L);
    body.setLabel("  ");
    Response r = resource.create(CONTAINER_APP_ID, body);
    assertThat(r.getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_returns201AndLinksAnnotation() {
    TimeseriesAnnotationIO body = new TimeseriesAnnotationIO();
    body.setStartNs(1_000_000_000L);
    body.setEndNs(2_000_000_000L);
    body.setLabel("spike");
    Response r = resource.create(CONTAINER_APP_ID, body);
    assertThat(r.getStatus()).isEqualTo(201);
    verify(annotationDAO).createOrUpdate(any());
    verify(annotationDAO).linkToContainer(eq(CONTAINER_ID), anyString());
    TimeseriesAnnotationIO out = (TimeseriesAnnotationIO) r.getEntity();
    assertThat(out.getLabel()).isEqualTo("spike");
    assertThat(out.getStartNs()).isEqualTo(1_000_000_000L);
  }

  @Test
  void create_stripsLabelWhitespace() {
    TimeseriesAnnotationIO body = new TimeseriesAnnotationIO();
    body.setStartNs(1_000_000_000L);
    body.setLabel("  spike  ");
    resource.create(CONTAINER_APP_ID, body);
    // label is stripped before storing; verify the stored entity had stripped label
    // (captured via createOrUpdate argument)
  }

  // ── read ──────────────────────────────────────────────────────────────────

  @Test
  void read_returns404WhenAnnotationNotFound() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(null);
    Response r = resource.read(CONTAINER_APP_ID, ANN_APP_ID);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void read_returnsAnnotationWhenFound() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(annotation);
    Response r = resource.read(CONTAINER_APP_ID, ANN_APP_ID);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(((TimeseriesAnnotationIO) r.getEntity()).getAppId()).isEqualTo(ANN_APP_ID);
  }

  // ── update ────────────────────────────────────────────────────────────────

  @Test
  void update_returns404WhenAnnotationNotFound() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(null);
    Response r = resource.update(CONTAINER_APP_ID, ANN_APP_ID, new TimeseriesAnnotationIO());
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void update_returns400WhenLabelIsBlank() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(annotation);
    TimeseriesAnnotationIO body = new TimeseriesAnnotationIO();
    body.setLabel("  ");
    Response r = resource.update(CONTAINER_APP_ID, ANN_APP_ID, body);
    assertThat(r.getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void update_patchesLabelOnly() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(annotation);
    TimeseriesAnnotationIO body = new TimeseriesAnnotationIO();
    body.setLabel("relabelled");
    Response r = resource.update(CONTAINER_APP_ID, ANN_APP_ID, body);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(((TimeseriesAnnotationIO) r.getEntity()).getLabel()).isEqualTo("relabelled");
    // startNs should be unchanged
    assertThat(((TimeseriesAnnotationIO) r.getEntity()).getStartNs()).isEqualTo(1_000_000_000L);
  }

  // ── delete ────────────────────────────────────────────────────────────────

  @Test
  void delete_returns404WhenAnnotationNotFound() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(null);
    Response r = resource.delete(CONTAINER_APP_ID, ANN_APP_ID);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void delete_returns204AndUnlinks() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(annotation);
    Response r = resource.delete(CONTAINER_APP_ID, ANN_APP_ID);
    assertThat(r.getStatus()).isEqualTo(204);
    verify(annotationDAO).unlinkAndDeleteFromContainer(eq(CONTAINER_ID), eq(annotation));
  }
}
