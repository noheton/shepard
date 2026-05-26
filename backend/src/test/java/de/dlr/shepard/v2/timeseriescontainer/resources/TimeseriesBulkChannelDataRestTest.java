package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkChannelDataRequestIO;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the TS-OPT2 bulk raw-data endpoint:
 * {@code POST /v2/timeseries-containers/{containerId}/channels/data/bulk}.
 */
public class TimeseriesBulkChannelDataRestTest {

  private TimeseriesContainerChannelsRest resource;
  private TsChannelResolver resolverMock;
  private TimeseriesService serviceMock;
  private TimeseriesContainerService containerServiceMock;

  private static final long   CONTAINER_ID = 42L;
  private static final long   START_NS     = 1_000_000_000L;
  private static final long   END_NS       = 2_000_000_000L;
  private static final UUID   KNOWN_A      = UUID.fromString("00000000-0000-4000-8000-000000000010");
  private static final UUID   KNOWN_B      = UUID.fromString("00000000-0000-4000-8000-000000000011");
  private static final UUID   UNKNOWN      = UUID.fromString("00000000-0000-4000-8000-000000000099");

  @BeforeEach
  void setUp() throws Exception {
    resource             = new TimeseriesContainerChannelsRest();
    resolverMock         = mock(TsChannelResolver.class);
    serviceMock          = mock(TimeseriesService.class);
    containerServiceMock = mock(TimeseriesContainerService.class);
    inject(resource, "tsChannelResolver",         resolverMock);
    inject(resource, "timeseriesService",          serviceMock);
    inject(resource, "timeseriesContainerService", containerServiceMock);
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static Timeseries tuple(String measurement) {
    return new Timeseries(measurement, "AFP-1", "head", "sym", "field");
  }

  // ── Container permission check ─────────────────────────────────────────────

  @Test
  void alwaysChecksContainerPermission() {
    when(resolverMock.resolveTuple(any())).thenReturn(Optional.empty());
    when(serviceMock.getManyTimeseriesWithDataPoints(anyLong(), any(), any())).thenReturn(List.of());

    resource.getBulkChannelData(CONTAINER_ID,
      new BulkChannelDataRequestIO(List.of(UNKNOWN), START_NS, END_NS));

    verify(containerServiceMock).getContainer(CONTAINER_ID);
  }

  // ── Unknown IDs silently skipped ───────────────────────────────────────────

  @Test
  void allUnknownIds_returnsEmpty() {
    when(resolverMock.resolveTuple(UNKNOWN)).thenReturn(Optional.empty());
    when(serviceMock.getManyTimeseriesWithDataPoints(anyLong(), any(), any())).thenReturn(List.of());

    Response resp = resource.getBulkChannelData(CONTAINER_ID,
      new BulkChannelDataRequestIO(List.of(UNKNOWN), START_NS, END_NS));

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<TimeseriesWithDataPoints> body = (List<TimeseriesWithDataPoints>) resp.getEntity();
    assertEquals(0, body.size());
  }

  // ── Mixed known + unknown ──────────────────────────────────────────────────

  @Test
  void mixedIds_unknownSkipped_knownFetched() {
    Timeseries ta = tuple("vibration");
    when(resolverMock.resolveTuple(KNOWN_A)).thenReturn(Optional.of(ta));
    when(resolverMock.resolveTuple(UNKNOWN)).thenReturn(Optional.empty());
    TimeseriesWithDataPoints expected = new TimeseriesWithDataPoints(ta, List.of());
    when(serviceMock.getManyTimeseriesWithDataPoints(anyLong(), any(), any()))
      .thenReturn(List.of(expected));

    Response resp = resource.getBulkChannelData(CONTAINER_ID,
      new BulkChannelDataRequestIO(List.of(KNOWN_A, UNKNOWN), START_NS, END_NS));

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<TimeseriesWithDataPoints> body = (List<TimeseriesWithDataPoints>) resp.getEntity();
    assertEquals(1, body.size());
  }

  // ── Two known channels ─────────────────────────────────────────────────────

  @Test
  void twoKnownIds_delegatesCorrectTuplesToService() {
    Timeseries ta = tuple("channel_a");
    Timeseries tb = tuple("channel_b");
    when(resolverMock.resolveTuple(KNOWN_A)).thenReturn(Optional.of(ta));
    when(resolverMock.resolveTuple(KNOWN_B)).thenReturn(Optional.of(tb));
    when(serviceMock.getManyTimeseriesWithDataPoints(
        anyLong(), any(), any(TimeseriesDataPointsQueryParams.class)))
      .thenReturn(List.of(
        new TimeseriesWithDataPoints(ta, List.of()),
        new TimeseriesWithDataPoints(tb, List.of())));

    Response resp = resource.getBulkChannelData(CONTAINER_ID,
      new BulkChannelDataRequestIO(List.of(KNOWN_A, KNOWN_B), START_NS, END_NS));

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<TimeseriesWithDataPoints> body = (List<TimeseriesWithDataPoints>) resp.getEntity();
    assertEquals(2, body.size());
    verify(serviceMock).getManyTimeseriesWithDataPoints(
      anyLong(), any(), any(TimeseriesDataPointsQueryParams.class));
  }

  // ── Correct time window forwarded ─────────────────────────────────────────

  @Test
  void timeWindow_forwardedToService() {
    Timeseries ta = tuple("sensor");
    when(resolverMock.resolveTuple(KNOWN_A)).thenReturn(Optional.of(ta));
    when(serviceMock.getManyTimeseriesWithDataPoints(anyLong(), any(), any())).thenReturn(List.of());

    resource.getBulkChannelData(CONTAINER_ID,
      new BulkChannelDataRequestIO(List.of(KNOWN_A), START_NS, END_NS));

    verify(serviceMock).getManyTimeseriesWithDataPoints(
      anyLong(),
      any(),
      org.mockito.ArgumentMatchers.eq(
        new TimeseriesDataPointsQueryParams(START_NS, END_NS, null, null, null)));
  }
}
