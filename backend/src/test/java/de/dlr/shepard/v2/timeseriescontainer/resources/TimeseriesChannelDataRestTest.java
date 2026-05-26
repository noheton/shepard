package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the TS-IDc data endpoint:
 * {@code GET /v2/timeseries-containers/{containerId}/channels/{shepardId}/data}.
 *
 * <p>Tests the routing logic (404 on unknown shepardId, 200 on known) and
 * the LTTB opt-in path. The Panache JPA query paths inside the resolver are
 * exercised by the integration suite; these tests cover the REST resource's
 * decision branches without a live database.
 */
public class TimeseriesChannelDataRestTest {

  private TimeseriesContainerChannelsRest resource;
  private TsChannelResolver resolverMock;
  private TimeseriesService serviceMock;
  private TimeseriesContainerService containerServiceMock;

  private static final long CONTAINER_ID = 42L;
  private static final UUID KNOWN_ID     = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID UNKNOWN_ID   = UUID.fromString("00000000-0000-4000-8000-000000000002");
  private static final long START_NS     = 1_000_000_000L;
  private static final long END_NS       = 2_000_000_000L;

  @BeforeEach
  void setUp() throws Exception {
    resource             = new TimeseriesContainerChannelsRest();
    resolverMock         = mock(TsChannelResolver.class);
    serviceMock          = mock(TimeseriesService.class);
    containerServiceMock = mock(TimeseriesContainerService.class);

    inject(resource, "tsChannelResolver",        resolverMock);
    inject(resource, "timeseriesService",         serviceMock);
    inject(resource, "timeseriesContainerService", containerServiceMock);
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

  // ── 404 on unknown shepardId ────────────────────────────────────────────────

  @Test
  void returnsNotFound_whenShepardIdUnknown() {
    when(resolverMock.resolveTuple(UNKNOWN_ID)).thenReturn(Optional.empty());

    Response resp = resource.getChannelData(CONTAINER_ID, UNKNOWN_ID, START_NS, END_NS, null, null);

    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), resp.getStatus());
  }

  // ── 200 with data on known shepardId ───────────────────────────────────────

  @Test
  void returnsOk_whenShepardIdKnown() {
    Timeseries tuple = aTuple();
    when(resolverMock.resolveTuple(KNOWN_ID)).thenReturn(Optional.of(tuple));
    when(serviceMock.getDataPointsByTimeseries(
        CONTAINER_ID, tuple, new TimeseriesDataPointsQueryParams(START_NS, END_NS, null, null, null)))
      .thenReturn(List.of());

    Response resp = resource.getChannelData(CONTAINER_ID, KNOWN_ID, START_NS, END_NS, null, null);

    assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());
    assertNotNull(resp.getEntity());
  }

  @Test
  void responseEntity_isTimeseriesWithDataPoints() {
    Timeseries tuple = aTuple();
    when(resolverMock.resolveTuple(KNOWN_ID)).thenReturn(Optional.of(tuple));
    when(serviceMock.getDataPointsByTimeseries(
        CONTAINER_ID, tuple, new TimeseriesDataPointsQueryParams(START_NS, END_NS, null, null, null)))
      .thenReturn(List.of());

    Response resp = resource.getChannelData(CONTAINER_ID, KNOWN_ID, START_NS, END_NS, null, null);

    assertEquals(TimeseriesWithDataPoints.class, resp.getEntity().getClass(),
      "entity must be TimeseriesWithDataPoints so it serialises like the v1 shape");
  }

  // ── LTTB opt-in ────────────────────────────────────────────────────────────

  @Test
  void lttbDownsample_isSilentlySkipped_whenPointListIsNull() {
    Timeseries tuple = aTuple();
    when(resolverMock.resolveTuple(KNOWN_ID)).thenReturn(Optional.of(tuple));
    when(serviceMock.getDataPointsByTimeseries(
        CONTAINER_ID, tuple, new TimeseriesDataPointsQueryParams(START_NS, END_NS, null, null, null)))
      .thenReturn(null);

    // should not NPE even when points == null and downsample requested
    Response resp = resource.getChannelData(CONTAINER_ID, KNOWN_ID, START_NS, END_NS, "lttb", 500);

    assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());
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

    Response resp = resource.getChannelData(CONTAINER_ID, KNOWN_ID, START_NS, END_NS, null, null);

    @SuppressWarnings("unchecked")
    TimeseriesWithDataPoints body = (TimeseriesWithDataPoints) resp.getEntity();
    assertEquals(3, body.getPoints().size(), "points must be unmodified when downsample is absent");
  }

  // ── container permission check is always called ────────────────────────────

  @Test
  void alwaysChecksContainerPermission() {
    when(resolverMock.resolveTuple(UNKNOWN_ID)).thenReturn(Optional.empty());

    resource.getChannelData(CONTAINER_ID, UNKNOWN_ID, START_NS, END_NS, null, null);

    verify(containerServiceMock).getContainer(CONTAINER_ID);
  }
}
