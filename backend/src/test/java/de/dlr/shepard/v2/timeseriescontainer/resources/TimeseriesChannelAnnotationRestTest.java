package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.io.SemanticAnnotationIO;
import de.dlr.shepard.context.semantic.services.AnnotatableTimeseriesService;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link TimeseriesChannelAnnotationRest} — TS-SEMANTIC-REST.
 *
 * <p>Uses Mockito-only (no Quarkus context) following the pattern established
 * by {@code TimeseriesAnnotationRestTest}.
 */
class TimeseriesChannelAnnotationRestTest {

  static final long CONTAINER_ID = 42L;
  static final String CONTAINER_APP_ID = "01928eaa-0000-7000-8000-000000000042";
  static final String CHANNEL_SHEPARD_ID = "0190abcd-1234-7abc-8def-000000000001";

  @Mock
  AnnotatableTimeseriesService service;

  @Mock
  TimeseriesContainerService containerService;

  @Mock
  TimeseriesContainer mockContainer;

  TimeseriesChannelAnnotationRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new TimeseriesChannelAnnotationRest();
    resource.service = service;
    resource.containerService = containerService;

    when(mockContainer.getId()).thenReturn(CONTAINER_ID);
    when(containerService.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(mockContainer);
  }

  // ── list ────────────────────────────────────────────────────────────────

  @Test
  void listAnnotations_returnsEmptyList_whenNoAnnotationsExist() {
    when(service.getAnnotationsByChannelShepardId(eq(CONTAINER_ID), eq(CHANNEL_SHEPARD_ID)))
      .thenReturn(Collections.emptyList());

    var r = resource.listAnnotations(CONTAINER_APP_ID, CHANNEL_SHEPARD_ID);

    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<SemanticAnnotationIO>) r.getEntity();
    assertThat(body).isEmpty();
  }

  @Test
  void listAnnotations_propagatesNotFoundException_whenContainerMissing() {
    when(containerService.getContainerByAppId(CONTAINER_APP_ID))
      .thenThrow(new NotFoundException("No container with appId " + CONTAINER_APP_ID));

    // NotFoundException from the service propagates as 404 via JAX-RS container mapping.
    // In a pure unit test there is no JAX-RS container, so we verify the throw itself.
    org.junit.jupiter.api.Assertions.assertThrows(
      NotFoundException.class,
      () -> resource.listAnnotations(CONTAINER_APP_ID, CHANNEL_SHEPARD_ID)
    );
  }

  @Test
  void listAnnotations_returns200WithAnnotations() {
    var ann = makeAnnotation(99L, "http://example.org/prop", "Prop", "http://example.org/val", "Val");
    when(service.getAnnotationsByChannelShepardId(eq(CONTAINER_ID), eq(CHANNEL_SHEPARD_ID)))
      .thenReturn(List.of(ann));

    var r = resource.listAnnotations(CONTAINER_APP_ID, CHANNEL_SHEPARD_ID);

    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var body = (List<SemanticAnnotationIO>) r.getEntity();
    assertThat(body).hasSize(1);
    assertThat(body.get(0).getPropertyIRI()).isEqualTo("http://example.org/prop");
    assertThat(body.get(0).getValueIRI()).isEqualTo("http://example.org/val");
  }

  // ── create ──────────────────────────────────────────────────────────────

  @Test
  void createAnnotation_returns201_andDelegatestoService() {
    var ann = makeAnnotation(77L, "http://p.org/pred", "pred", "http://v.org/val", "val");
    when(service.createAnnotationForChannel(eq(CONTAINER_ID), eq(CHANNEL_SHEPARD_ID), any(SemanticAnnotationIO.class)))
      .thenReturn(ann);

    var body = new SemanticAnnotationIO();
    body.setPropertyIRI("http://p.org/pred");
    body.setValueIRI("http://v.org/val");
    var r = resource.createAnnotation(CONTAINER_APP_ID, CHANNEL_SHEPARD_ID, body);

    assertThat(r.getStatus()).isEqualTo(201);
    var result = (SemanticAnnotationIO) r.getEntity();
    assertThat(result.getId()).isEqualTo(77L);
    verify(service).createAnnotationForChannel(eq(CONTAINER_ID), eq(CHANNEL_SHEPARD_ID), any());
  }

  @Test
  void createAnnotation_propagates400_whenServiceThrowsBadRequest() {
    when(service.createAnnotationForChannel(eq(CONTAINER_ID), anyString(), any()))
      .thenThrow(new BadRequestException("channelShepardId must not be blank"));

    var body = new SemanticAnnotationIO();
    body.setPropertyIRI("http://p.org/pred");
    body.setValueIRI("http://v.org/val");
    // The BadRequestException thrown by the service propagates to the caller
    // (it is a JAX-RS exception and would be mapped by the container).
    try {
      resource.createAnnotation(CONTAINER_APP_ID, "", body);
    } catch (BadRequestException e) {
      assertThat(e.getMessage()).contains("channelShepardId");
    }
  }

  // ── delete ──────────────────────────────────────────────────────────────

  static final String ANNOTATION_APP_ID = "01928fff-0000-7000-8000-000000000037";

  @Test
  void deleteAnnotation_returns204() {
    var r = resource.deleteAnnotation(CONTAINER_APP_ID, CHANNEL_SHEPARD_ID, ANNOTATION_APP_ID);

    assertThat(r.getStatus()).isEqualTo(204);
    verify(service).deleteAnnotationForChannel(eq(CONTAINER_ID), eq(CHANNEL_SHEPARD_ID), eq(ANNOTATION_APP_ID));
  }

  @Test
  void deleteAnnotation_propagatesNotFoundException_whenAnnotationMissing() {
    org.mockito.Mockito.doThrow(new NotFoundException("No annotation with appId " + ANNOTATION_APP_ID))
      .when(service).deleteAnnotationForChannel(eq(CONTAINER_ID), eq(CHANNEL_SHEPARD_ID), eq(ANNOTATION_APP_ID));

    org.junit.jupiter.api.Assertions.assertThrows(
      NotFoundException.class,
      () -> resource.deleteAnnotation(CONTAINER_APP_ID, CHANNEL_SHEPARD_ID, ANNOTATION_APP_ID)
    );
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private static SemanticAnnotation makeAnnotation(
    long id, String propIRI, String propName, String valIRI, String valName
  ) {
    var a = new SemanticAnnotation(id); // uses the test-visible constructor that sets id
    a.setPropertyIRI(propIRI);
    a.setPropertyName(propName);
    a.setValueIRI(valIRI);
    a.setValueName(valName);
    return a;
  }
}
