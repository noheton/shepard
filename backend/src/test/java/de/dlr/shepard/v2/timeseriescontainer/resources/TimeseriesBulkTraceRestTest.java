package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkTraceChannelIO;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkTraceRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkTraceResultIO;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the TS-OPT2 bulk-trace endpoint:
 * {@code POST /v2/timeseries-containers/{containerId}/channels/bulk}.
 *
 * <p>Verifies: container permission check, role echo, empty-result on unknown channel,
 * LTTB delegation, and raw-fetch fallback.
 */
class TimeseriesBulkTraceRestTest {

  private TimeseriesContainerChannelsRest resource;
  private TimeseriesService serviceMock;
  private TimeseriesContainerService containerServiceMock;

  private static final long CONTAINER_ID = 42L;
  private static final long START_NS     = 1_000_000_000L;
  private static final long END_NS       = 2_000_000_000L;

  @BeforeEach
  void setUp() throws Exception {
    resource             = new TimeseriesContainerChannelsRest();
    serviceMock          = mock(TimeseriesService.class);
    containerServiceMock = mock(TimeseriesContainerService.class);
    inject(resource, "timeseriesService",          serviceMock);
    inject(resource, "timeseriesContainerService", containerServiceMock);
    // tsChannelResolver not needed for the bulk-trace path (5-tuple resolution happens inline)
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static BulkTraceChannelIO ch(String role, String measurement, String field) {
    return new BulkTraceChannelIO(role, measurement, null, null, null, field);
  }

  private static TimeseriesDataPoint pt(long t, double v) {
    return new TimeseriesDataPoint(t, v);
  }

  // ── Container permission check ─────────────────────────────────────────────

  @Test
  void alwaysChecksContainerPermission() {
    when(serviceMock.getDataPointsLttbOptimised(anyLong(), any(), anyLong(), anyLong(), anyInt()))
      .thenReturn(List.of());

    resource.getBulkTraceData(CONTAINER_ID,
      new BulkTraceRequestIO(START_NS, END_NS, "lttb", null, List.of(ch("x", "tcp", "X"))));

    verify(containerServiceMock).getContainer(CONTAINER_ID);
  }

  // ── Role label echoed verbatim ─────────────────────────────────────────────

  @Test
  void roleLabel_echoedInResult() {
    when(serviceMock.getDataPointsLttbOptimised(anyLong(), any(), anyLong(), anyLong(), anyInt()))
      .thenReturn(List.of(pt(START_NS, 1.0)));

    Response resp = resource.getBulkTraceData(CONTAINER_ID,
      new BulkTraceRequestIO(START_NS, END_NS, "lttb", null,
        List.of(ch("rot_a", "kinematics", "degrees"))));

    assertThat(resp.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<BulkTraceResultIO> body = (List<BulkTraceResultIO>) resp.getEntity();
    assertThat(body).hasSize(1);
    assertThat(body.get(0).role()).isEqualTo("rot_a");
  }

  // ── Unknown channel returns empty points, not 404 ─────────────────────────

  @Test
  void unknownChannel_returnsEmptyPoints() {
    when(serviceMock.getDataPointsLttbOptimised(anyLong(), any(), anyLong(), anyLong(), anyInt()))
      .thenReturn(List.of());

    Response resp = resource.getBulkTraceData(CONTAINER_ID,
      new BulkTraceRequestIO(START_NS, END_NS, "lttb", null,
        List.of(ch("x", "no_such_measurement", "no_such_field"))));

    assertThat(resp.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<BulkTraceResultIO> body = (List<BulkTraceResultIO>) resp.getEntity();
    assertThat(body.get(0).points()).isEmpty();
  }

  // ── Multi-role: all roles returned ────────────────────────────────────────

  @Test
  void multiRole_allRolesReturned() {
    when(serviceMock.getDataPointsLttbOptimised(anyLong(), any(), anyLong(), anyLong(), anyInt()))
      .thenReturn(List.of(pt(START_NS, 0.0)));

    Response resp = resource.getBulkTraceData(CONTAINER_ID,
      new BulkTraceRequestIO(START_NS, END_NS, "lttb", 500,
        List.of(ch("x", "tcp", "X"), ch("y", "tcp", "Y"), ch("z", "tcp", "Z"))));

    assertThat(resp.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<BulkTraceResultIO> body = (List<BulkTraceResultIO>) resp.getEntity();
    assertThat(body).extracting(BulkTraceResultIO::role)
      .containsExactlyInAnyOrder("x", "y", "z");
  }

  // ── LTTB path used when downsample=lttb ───────────────────────────────────

  @Test
  void lttbFlag_delegatesToLttbMethod() {
    when(serviceMock.getDataPointsLttbOptimised(anyLong(), any(), anyLong(), anyLong(), anyInt()))
      .thenReturn(List.of());

    resource.getBulkTraceData(CONTAINER_ID,
      new BulkTraceRequestIO(START_NS, END_NS, "lttb", 1000,
        List.of(ch("x", "tcp", "X"))));

    verify(serviceMock).getDataPointsLttbOptimised(
      anyLong(), any(), anyLong(), anyLong(), anyInt());
  }

  // ── Raw fetch when downsample absent ──────────────────────────────────────

  @Test
  void noDownsample_delegatesToRawFetch() {
    when(serviceMock.getDataPointsByTimeseriesActivatedRequestContext(
        anyLong(), any(), any(TimeseriesDataPointsQueryParams.class)))
      .thenReturn(List.of());

    resource.getBulkTraceData(CONTAINER_ID,
      new BulkTraceRequestIO(START_NS, END_NS, null, null,
        List.of(ch("x", "tcp", "X"))));

    verify(serviceMock).getDataPointsByTimeseriesActivatedRequestContext(
      anyLong(), any(), any(TimeseriesDataPointsQueryParams.class));
  }
}
