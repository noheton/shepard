package de.dlr.shepard.v2.containers.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-CONT-NS-COLLAPSE-4 — unit tests for the live-window path
 * on {@link TimeseriesContainerKindHandler}, replacing the deleted
 * {@code TimeseriesLiveWindowShepardIdTest}.
 *
 * <p>Verifies the key decision branches for shepardId vs. 5-tuple channel lookup.
 */
class TimeseriesContainerKindHandlerLiveWindowTest {

  private static final String CONTAINER_APP_ID = "01930000-0000-7000-8000-000000000abc";
  private static final long CONTAINER_NUMERIC_ID = 1772L;
  private static final UUID KNOWN_SHEPARD_ID =
      UUID.fromString("a2c0f1dd-4dce-4400-92e4-445cd18826e6");
  private static final UUID UNKNOWN_SHEPARD_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000099");

  @Mock
  TimeseriesContainerService service;

  @Mock
  TsChannelResolver channelResolver;

  @Mock
  TimeseriesDataPointRepository dataPointRepository;

  TimeseriesContainerKindHandler handler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new TimeseriesContainerKindHandler();
    handler.service = service;
    handler.channelResolver = channelResolver;
    handler.dataPointRepository = dataPointRepository;
    // Other injections not needed for live-window tests
    handler.dao = null;
    handler.userService = null;
    handler.dateHelper = null;
    handler.annotatableTimeseriesService = null;
    handler.annotationDAO = null;

    TimeseriesContainer container = mock(TimeseriesContainer.class);
    when(container.getId()).thenReturn(CONTAINER_NUMERIC_ID);
    when(service.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);
  }

  private static TimeseriesEntity row(long containerId, UUID shepardId) {
    TimeseriesEntity row = new TimeseriesEntity(
        containerId, "mffd", "consolidation_force_N",
        "AFP-AFPT-MTLH-S1", "ZLP-Augsburg",
        "afp-s1_consolidation_force_N", DataPointValueType.Double);
    row.setShepardId(shepardId);
    return row;
  }

  // ── happy path with shepardId → returns Optional<200> ───────────────────

  @Test
  void shepardId_knownInContainer_returnsPresent200() {
    TimeseriesEntity entity = row(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID);
    when(channelResolver.findByContainerAndShepardId(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID))
        .thenReturn(Optional.of(entity));
    when(dataPointRepository.queryDataPoints(eq(entity.getId()), any(), any()))
        .thenReturn(List.of());

    var result = handler.getLiveWindow(
        CONTAINER_APP_ID, KNOWN_SHEPARD_ID, null, null, null, null, null, 300, true);

    assertTrue(result.isPresent());
    assertEquals(Response.Status.OK.getStatusCode(), result.get().getStatus());
    verify(channelResolver).findByContainerAndShepardId(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID);
    verify(channelResolver, never()).findByContainerAndPartialTuple(
        eq(CONTAINER_NUMERIC_ID), any(), any(), any(), any(), any());
  }

  // ── shepardId not found → returns Optional<404> ──────────────────────────

  @Test
  void shepardId_unknown_returnsPresent404() {
    when(channelResolver.findByContainerAndShepardId(CONTAINER_NUMERIC_ID, UNKNOWN_SHEPARD_ID))
        .thenReturn(Optional.empty());

    var result = handler.getLiveWindow(
        CONTAINER_APP_ID, UNKNOWN_SHEPARD_ID, null, null, null, null, null, 300, true);

    assertTrue(result.isPresent());
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), result.get().getStatus());
    verify(channelResolver, never()).findByContainerAndPartialTuple(
        eq(CONTAINER_NUMERIC_ID), any(), any(), any(), any(), any());
  }

  // ── 5-tuple filter, ambiguous match → returns Optional<400> ─────────────

  @Test
  void fiveTuple_ambiguousMatch_returnsPresent400() {
    TimeseriesEntity e1 = row(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID);
    TimeseriesEntity e2 = row(CONTAINER_NUMERIC_ID, UNKNOWN_SHEPARD_ID);
    when(channelResolver.findByContainerAndPartialTuple(
            CONTAINER_NUMERIC_ID, "mffd", null, null, null, null))
        .thenReturn(List.of(e1, e2));

    var result = handler.getLiveWindow(
        CONTAINER_APP_ID, null, "mffd", null, null, null, null, 300, true);

    assertTrue(result.isPresent());
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.get().getStatus());
  }

  // ── 5-tuple filter, no match → returns Optional<404> ────────────────────

  @Test
  void fiveTuple_noMatch_returnsPresent404() {
    when(channelResolver.findByContainerAndPartialTuple(
            CONTAINER_NUMERIC_ID, "nonexistent", null, null, null, null))
        .thenReturn(List.of());

    var result = handler.getLiveWindow(
        CONTAINER_APP_ID, null, "nonexistent", null, null, null, null, 300, true);

    assertTrue(result.isPresent());
    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), result.get().getStatus());
  }

  // ── container not found → service throws, propagates ────────────────────

  @Test
  void containerNotFound_serviceThrows_propagates() {
    when(service.getContainerByAppId(CONTAINER_APP_ID))
        .thenThrow(new jakarta.ws.rs.NotFoundException("No container"));

    org.junit.jupiter.api.Assertions.assertThrows(
        jakarta.ws.rs.NotFoundException.class,
        () -> handler.getLiveWindow(
            CONTAINER_APP_ID, KNOWN_SHEPARD_ID, null, null, null, null, null, 300, true));
  }
}
