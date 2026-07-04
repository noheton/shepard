package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.containers.handlers.TimeseriesContainerKindHandler;
import jakarta.ws.rs.NotFoundException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the TS-IDc data endpoint (APISIMP-CONT-NS-COLLAPSE-2):
 * {@code GET /v2/containers/{containerAppId}/channels/{shepardId}/data}.
 *
 * <p>Migrated from TimeseriesContainerChannelsRest (deleted in APISIMP-CONT-NS-COLLAPSE-2)
 * to {@link TimeseriesContainerKindHandler}. Tests the routing logic
 * (NotFoundException on unknown shepardId, Optional.of(data) on known) and
 * the LTTB opt-in path.
 */
public class TimeseriesChannelDataRestTest {

  private TimeseriesContainerKindHandler handler;
  private TsChannelResolver resolverMock;
  private TimeseriesService serviceMock;
  private TimeseriesContainerService containerServiceMock;

  private static final String CONTAINER_APP_ID = "00000000-0000-7000-8000-00000000002a";
  private static final long CONTAINER_ID = 42L;
  private static final UUID KNOWN_ID     = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID UNKNOWN_ID   = UUID.fromString("00000000-0000-4000-8000-000000000002");
  private static final long START_NS     = 1_000_000_000L;
  private static final long END_NS       = 2_000_000_000L;

  @BeforeEach
  void setUp() throws Exception {
    handler              = new TimeseriesContainerKindHandler();
    resolverMock         = mock(TsChannelResolver.class);
    serviceMock          = mock(TimeseriesService.class);
    containerServiceMock = mock(TimeseriesContainerService.class);

    inject(handler, "tsChannelResolver",  resolverMock);
    inject(handler, "timeseriesService",  serviceMock);
    inject(handler, "service",            containerServiceMock);

    var mockContainer = mock(TimeseriesContainer.class);
    when(mockContainer.getId()).thenReturn(CONTAINER_ID);
    when(containerServiceMock.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(mockContainer);
  }

  /** Use reflection to set CDI-injected fields. */
  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static Timeseries aTuple() {
    return new Timeseries("vibration", "AFP-1", "head", "ts1", "g_rms");
  }

  // ── NotFoundException on unknown shepardId ─────────────────────────────────

  @Test
  void throwsNotFound_whenShepardIdUnknown() {
    when(resolverMock.resolveTuple(UNKNOWN_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class,
        () -> handler.getChannelData(CONTAINER_APP_ID, UNKNOWN_ID, START_NS, END_NS, null, null));
  }

  // ── Optional.of(data) on known shepardId ──────────────────────────────────

  @Test
  void returnsPresent_whenShepardIdKnown() {
    Timeseries tuple = aTuple();
    when(resolverMock.resolveTuple(KNOWN_ID)).thenReturn(Optional.of(tuple));
    when(serviceMock.getDataPointsByTimeseries(
        CONTAINER_ID, tuple, new TimeseriesDataPointsQueryParams(START_NS, END_NS, null, null, null)))
      .thenReturn(List.of());

    Optional<TimeseriesWithDataPoints> result =
        handler.getChannelData(CONTAINER_APP_ID, KNOWN_ID, START_NS, END_NS, null, null);

    assertTrue(result.isPresent());
    assertNotNull(result.get());
  }

  @Test
  void responseEntity_isTimeseriesWithDataPoints() {
    Timeseries tuple = aTuple();
    when(resolverMock.resolveTuple(KNOWN_ID)).thenReturn(Optional.of(tuple));
    when(serviceMock.getDataPointsByTimeseries(
        CONTAINER_ID, tuple, new TimeseriesDataPointsQueryParams(START_NS, END_NS, null, null, null)))
      .thenReturn(List.of());

    Optional<TimeseriesWithDataPoints> result =
        handler.getChannelData(CONTAINER_APP_ID, KNOWN_ID, START_NS, END_NS, null, null);

    assertTrue(result.isPresent());
    assertEquals(TimeseriesWithDataPoints.class, result.get().getClass(),
      "entity must be TimeseriesWithDataPoints so it serialises like the v1 shape");
  }

  // ── LTTB opt-in ────────────────────────────────────────────────────────────

  @Test
  void lttbDownsample_returnsPresentEvenWhenPointListIsNull() {
    Timeseries tuple = aTuple();
    when(resolverMock.resolveTuple(KNOWN_ID)).thenReturn(Optional.of(tuple));
    // With downsample=lttb, the optimised path is taken — stub that, not the raw fetch.
    when(serviceMock.getDataPointsLttbOptimised(
        anyLong(), eq(tuple), anyLong(), anyLong(), anyInt()))
      .thenReturn(null);

    // Should not NPE even when points == null
    Optional<TimeseriesWithDataPoints> result =
        handler.getChannelData(CONTAINER_APP_ID, KNOWN_ID, START_NS, END_NS, "lttb", 500);

    assertTrue(result.isPresent());
  }

  @Test
  void lttbNotApplied_whenDownsampleParamAbsent() {
    Timeseries tuple = aTuple();
    List<TimeseriesDataPoint> pts = List.of(
      new TimeseriesDataPoint(1_000_000_000L, 1.0),
      new TimeseriesDataPoint(1_500_000_000L, 2.0),
      new TimeseriesDataPoint(2_000_000_000L, 3.0)
    );
    when(resolverMock.resolveTuple(KNOWN_ID)).thenReturn(Optional.of(tuple));
    when(serviceMock.getDataPointsByTimeseries(
        CONTAINER_ID, tuple, new TimeseriesDataPointsQueryParams(START_NS, END_NS, null, null, null)))
      .thenReturn(pts);

    Optional<TimeseriesWithDataPoints> result =
        handler.getChannelData(CONTAINER_APP_ID, KNOWN_ID, START_NS, END_NS, null, null);

    assertTrue(result.isPresent());
    assertEquals(3, result.get().getPoints().size(),
        "points must be unmodified when downsample is absent");
  }

  // ── LTTB optimised path (TS-OPT1) ─────────────────────────────────────────

  @Test
  void lttbPath_callsOptimisedService_notRawFetch() {
    Timeseries tuple = aTuple();
    List<TimeseriesDataPoint> pts = List.of(
      new TimeseriesDataPoint(START_NS, 1.0),
      new TimeseriesDataPoint(END_NS, 2.0)
    );
    when(resolverMock.resolveTuple(KNOWN_ID)).thenReturn(Optional.of(tuple));
    when(serviceMock.getDataPointsLttbOptimised(
        eq(CONTAINER_ID), eq(tuple), eq(START_NS), eq(END_NS), anyInt()))
      .thenReturn(pts);

    Optional<TimeseriesWithDataPoints> result =
        handler.getChannelData(CONTAINER_APP_ID, KNOWN_ID, START_NS, END_NS, "lttb", 200);

    assertTrue(result.isPresent());
    // Verify LTTB-optimised path was used
    verify(serviceMock).getDataPointsLttbOptimised(
        eq(CONTAINER_ID), eq(tuple), eq(START_NS), eq(END_NS), anyInt());
    // Verify raw-fetch was NOT called
    verify(serviceMock, never()).getDataPointsByTimeseries(anyLong(), any(), any());
  }

  @Test
  void lttbPath_clampsMaxPoints_toHardMax() {
    Timeseries tuple = aTuple();
    when(resolverMock.resolveTuple(KNOWN_ID)).thenReturn(Optional.of(tuple));
    when(serviceMock.getDataPointsLttbOptimised(
        eq(CONTAINER_ID), eq(tuple), eq(START_NS), eq(END_NS), eq(5000)))
      .thenReturn(List.of());

    handler.getChannelData(CONTAINER_APP_ID, KNOWN_ID, START_NS, END_NS, "lttb", 99_999);

    verify(serviceMock).getDataPointsLttbOptimised(
        eq(CONTAINER_ID), eq(tuple), eq(START_NS), eq(END_NS), eq(5000));
  }

  @Test
  void lttbPath_usesDefault_whenMaxPointsAbsent() {
    Timeseries tuple = aTuple();
    when(resolverMock.resolveTuple(KNOWN_ID)).thenReturn(Optional.of(tuple));
    when(serviceMock.getDataPointsLttbOptimised(
        eq(CONTAINER_ID), eq(tuple), eq(START_NS), eq(END_NS), eq(2000)))
      .thenReturn(List.of());

    handler.getChannelData(CONTAINER_APP_ID, KNOWN_ID, START_NS, END_NS, "lttb", null);

    verify(serviceMock).getDataPointsLttbOptimised(
        eq(CONTAINER_ID), eq(tuple), eq(START_NS), eq(END_NS), eq(2000));
  }

  @Test
  void noLttb_callsRawFetch_notOptimised() {
    Timeseries tuple = aTuple();
    when(resolverMock.resolveTuple(KNOWN_ID)).thenReturn(Optional.of(tuple));
    when(serviceMock.getDataPointsByTimeseries(anyLong(), any(), any())).thenReturn(List.of());

    handler.getChannelData(CONTAINER_APP_ID, KNOWN_ID, START_NS, END_NS, null, null);

    verify(serviceMock, never()).getDataPointsLttbOptimised(anyLong(), any(), anyLong(), anyLong(), anyInt());
  }

  // ── container permission check is always called ────────────────────────────

  @Test
  void alwaysChecksContainerPermission() {
    when(resolverMock.resolveTuple(UNKNOWN_ID)).thenReturn(Optional.empty());

    // NotFoundException thrown — but permission is checked before throw
    assertThrows(NotFoundException.class,
        () -> handler.getChannelData(CONTAINER_APP_ID, UNKNOWN_ID, START_NS, END_NS, null, null));

    verify(containerServiceMock).getContainerByAppId(CONTAINER_APP_ID);
  }
}
