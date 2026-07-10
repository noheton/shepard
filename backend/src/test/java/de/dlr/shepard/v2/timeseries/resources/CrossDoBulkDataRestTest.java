package de.dlr.shepard.v2.timeseries.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.DataObjectDAO;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.timeseries.io.CrossDoBulkDataRequestIO;
import de.dlr.shepard.v2.timeseries.io.CrossDoSeriesIO;
import de.dlr.shepard.v2.timeseries.services.CrossDoChannelResolver;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TS-CROSS-DO-VIEW-1 — unit coverage for
 * {@code POST /v2/data-objects/cross-bulk?kind=timeseries}.
 *
 * <p>Mirrors the {@code TimeseriesBulkChannelDataRestTest} mocking shape:
 * boundary mocks for DAO + service + permissions + resolver; LTTB step
 * mocked away.
 */
public class CrossDoBulkDataRestTest {

  private CrossDoBulkDataRest resource;
  private DataObjectDAO daoMock;
  private CrossDoChannelResolver resolverMock;
  private TimeseriesService serviceMock;
  private PermissionsService permsMock;
  private SecurityContext securityContext;

  private static final String DO_A = "01930a2b-fe4c-7e3c-9f1d-aaaaaaaaaaaa";
  private static final String DO_B = "01930a2b-fe4c-7e3c-9f1d-bbbbbbbbbbbb";
  private static final String DO_C = "01930a2b-fe4c-7e3c-9f1d-cccccccccccc";
  private static final String DO_FORBIDDEN = "01930a2b-fe4c-7e3c-9f1d-ffffffffffff";
  private static final String DO_UNKNOWN = "01930a2b-fe4c-7e3c-9f1d-999999999999";
  private static final String PREDICATE = "urn:shepard:afp:tcp-temperature-c";
  private static final long START_NS = 1_000_000_000L;
  private static final long END_NS = 2_000_000_000L;
  private static final String CALLER = "tester";

  @BeforeEach
  void setUp() throws Exception {
    resource = new CrossDoBulkDataRest();
    daoMock = mock(DataObjectDAO.class);
    resolverMock = mock(CrossDoChannelResolver.class);
    serviceMock = mock(TimeseriesService.class);
    permsMock = mock(PermissionsService.class);
    inject(resource, "dataObjectDAO", daoMock);
    inject(resource, "crossDoChannelResolver", resolverMock);
    inject(resource, "timeseriesService", serviceMock);
    inject(resource, "permissionsService", permsMock);

    securityContext = mock(SecurityContext.class);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(CALLER);
    when(securityContext.getUserPrincipal()).thenReturn(principal);
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  // ── kind discriminator: missing or unsupported kind → 400 ───────────────

  @Test
  void missingKind_returns400() {
    Response resp = resource.getCrossDoBulkData(
      null,
      new CrossDoBulkDataRequestIO(List.of(DO_A), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );
    assertEquals(400, resp.getStatus());
    verify(permsMock, never()).filterAllowedDataObjectAppIds(any(), any(), any());
  }

  @Test
  void unknownKind_returns400() {
    Response resp = resource.getCrossDoBulkData(
      "file",
      new CrossDoBulkDataRequestIO(List.of(DO_A), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );
    assertEquals(400, resp.getStatus());
    verify(permsMock, never()).filterAllowedDataObjectAppIds(any(), any(), any());
  }

  // ── Auth: unauthenticated caller is rejected ──────────────────────────────

  @Test
  void unauthenticatedCaller_returns401() {
    SecurityContext anon = mock(SecurityContext.class);
    when(anon.getUserPrincipal()).thenReturn(null);

    Response resp = resource.getCrossDoBulkData(
      "timeseries",
      new CrossDoBulkDataRequestIO(List.of(DO_A), PREDICATE, START_NS, END_NS, 500),
      anon
    );

    assertEquals(401, resp.getStatus());
    verify(permsMock, never()).filterAllowedDataObjectAppIds(any(), any(), any());
  }

  // ── Happy path: DO has matching channel, returns LTTB-downsampled series ──

  @Test
  void happyPath_returnsSeriesForResolvedChannel() {
    when(permsMock.filterAllowedDataObjectAppIds(any(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(Set.of(DO_A));
    when(daoMock.findNamesByAppIds(any())).thenReturn(Map.of(DO_A, "Track_244__Run_30239"));
    when(resolverMock.resolveChannelsByPredicate(DO_A, PREDICATE))
      .thenReturn(List.of(new CrossDoChannelResolver.ResolvedChannel(
        42L, "afp", "robot-1", "head", "tcp_temp", "value"
      )));
    List<TimeseriesDataPoint> points = List.of(
      new TimeseriesDataPoint(1_100_000_000L, 180.0),
      new TimeseriesDataPoint(1_900_000_000L, 220.0)
    );
    when(serviceMock.getDataPointsLttbOptimised(eq(42L), any(Timeseries.class), eq(START_NS), eq(END_NS), eq(500)))
      .thenReturn(points);

    Response resp = resource.getCrossDoBulkData(
      "timeseries",
      new CrossDoBulkDataRequestIO(List.of(DO_A), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<CrossDoSeriesIO> body = ((PagedResponseIO<CrossDoSeriesIO>) resp.getEntity()).items();
    assertEquals(1, body.size());
    CrossDoSeriesIO s = body.get(0);
    assertEquals(DO_A, s.dataObjectAppId());
    assertEquals("Track_244__Run_30239", s.dataObjectName());
    assertEquals(PREDICATE, s.channelKey());
    assertEquals("tcp_temp", s.channelSymbolicName());
    assertEquals(2, s.points().size());
  }

  // ── DO has no matching channel: empty points but still represented ────────

  @Test
  void doWithoutMatchingChannel_returnsEmptyPointsRow() {
    when(permsMock.filterAllowedDataObjectAppIds(any(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(Set.of(DO_B));
    when(daoMock.findNamesByAppIds(any())).thenReturn(Map.of(DO_B, "Track_245"));
    when(resolverMock.resolveChannelsByPredicate(DO_B, PREDICATE)).thenReturn(List.of());

    Response resp = resource.getCrossDoBulkData(
      "timeseries",
      new CrossDoBulkDataRequestIO(List.of(DO_B), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<CrossDoSeriesIO> body = ((PagedResponseIO<CrossDoSeriesIO>) resp.getEntity()).items();
    assertEquals(1, body.size());
    CrossDoSeriesIO s = body.get(0);
    assertEquals(DO_B, s.dataObjectAppId());
    assertEquals("Track_245", s.dataObjectName());
    assertNull(s.channelSymbolicName());
    assertTrue(s.points().isEmpty());
    verify(serviceMock, never()).getDataPointsLttbOptimised(anyLong(), any(), anyLong(), anyLong(), anyInt());
  }

  // ── Permission denial: silently dropped, never 403 ────────────────────────

  @Test
  void permissionDenied_silentlyDropped() {
    // Batch permission check returns only the allowed DO; forbidden DO excluded.
    when(permsMock.filterAllowedDataObjectAppIds(any(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(Set.of(DO_A));
    when(daoMock.findNamesByAppIds(any())).thenReturn(Map.of(DO_A, "ok"));
    when(resolverMock.resolveChannelsByPredicate(DO_A, PREDICATE)).thenReturn(List.of());

    Response resp = resource.getCrossDoBulkData(
      "timeseries",
      new CrossDoBulkDataRequestIO(List.of(DO_FORBIDDEN, DO_A), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<CrossDoSeriesIO> body = ((PagedResponseIO<CrossDoSeriesIO>) resp.getEntity()).items();
    assertEquals(1, body.size(), "forbidden DO must be dropped silently, allowed DO kept");
    assertEquals(DO_A, body.get(0).dataObjectAppId());
  }

  // ── Unknown DO appId: silently dropped (not in the collection graph) ──────

  @Test
  void unknownAppId_silentlyDropped() {
    // A DO that doesn't exist in the collection graph won't appear in the allowed set.
    when(permsMock.filterAllowedDataObjectAppIds(any(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(Set.of());
    when(daoMock.findNamesByAppIds(any())).thenReturn(Map.of());

    Response resp = resource.getCrossDoBulkData(
      "timeseries",
      new CrossDoBulkDataRequestIO(List.of(DO_UNKNOWN), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<CrossDoSeriesIO> body = ((PagedResponseIO<CrossDoSeriesIO>) resp.getEntity()).items();
    assertTrue(body.isEmpty());
    verify(resolverMock, never()).resolveChannelsByPredicate(eq(DO_UNKNOWN), any());
    verify(serviceMock, never()).getDataPointsLttbOptimised(anyLong(), any(), anyLong(), anyLong(), anyInt());
  }

  // ── Mix of one allowed + one no-channel + one forbidden + one unknown ────

  @Test
  void mixedShapes_orderPreserved() {
    // Batch permission check: only DO_A and DO_B allowed; DO_FORBIDDEN and DO_C excluded.
    when(permsMock.filterAllowedDataObjectAppIds(any(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(Set.of(DO_A, DO_B));
    when(daoMock.findNamesByAppIds(any())).thenReturn(Map.of(DO_A, "A", DO_B, "B"));

    // DO_A: has a channel with data
    when(resolverMock.resolveChannelsByPredicate(DO_A, PREDICATE))
      .thenReturn(List.of(new CrossDoChannelResolver.ResolvedChannel(
        1L, "m", "d", "l", "sym", "f"
      )));
    when(serviceMock.getDataPointsLttbOptimised(eq(1L), any(), anyLong(), anyLong(), anyInt()))
      .thenReturn(List.of(new TimeseriesDataPoint(1_000_000_000L, 1.0)));

    // DO_B: no channel match
    when(resolverMock.resolveChannelsByPredicate(DO_B, PREDICATE)).thenReturn(List.of());

    Response resp = resource.getCrossDoBulkData(
      "timeseries",
      new CrossDoBulkDataRequestIO(
        List.of(DO_A, DO_B, DO_FORBIDDEN, DO_C),
        PREDICATE, START_NS, END_NS, 500
      ),
      securityContext
    );

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<CrossDoSeriesIO> body = ((PagedResponseIO<CrossDoSeriesIO>) resp.getEntity()).items();
    // 2 rows: A (data), B (no channel). Forbidden and unknown DOs silently dropped.
    assertEquals(2, body.size());
    assertEquals(DO_A, body.get(0).dataObjectAppId());
    assertEquals(DO_B, body.get(1).dataObjectAppId());
    assertNotNull(body.get(0).channelSymbolicName());
    assertNull(body.get(1).channelSymbolicName());
  }

  // ── Downsample clamping ──────────────────────────────────────────────────

  @Test
  void downsampleClamp_nullDefaultsTo500() {
    assertEquals(500, CrossDoBulkDataRest.clampDownsample(null));
  }

  @Test
  void downsampleClamp_zeroClampsToOne() {
    assertEquals(1, CrossDoBulkDataRest.clampDownsample(0));
    assertEquals(1, CrossDoBulkDataRest.clampDownsample(-9));
  }

  @Test
  void downsampleClamp_aboveMaxClampsToMax() {
    assertEquals(CrossDoBulkDataRest.HARD_MAX_DOWNSAMPLE,
      CrossDoBulkDataRest.clampDownsample(50_000));
  }

  @Test
  void downsampleClamp_inRangePreserved() {
    assertEquals(1234, CrossDoBulkDataRest.clampDownsample(1234));
  }

  // ── Multi-DO LTTB-routing is called once per channel-bearing DO ──────────

  @Test
  void multipleResolvedDOs_lttbCalledPerSeries() {
    when(permsMock.filterAllowedDataObjectAppIds(any(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(Set.of(DO_A, DO_B));
    when(daoMock.findNamesByAppIds(any())).thenReturn(Map.of(DO_A, "A", DO_B, "B"));

    when(resolverMock.resolveChannelsByPredicate(DO_A, PREDICATE))
      .thenReturn(List.of(new CrossDoChannelResolver.ResolvedChannel(1L, "m", "d", "l", "symA", "f")));
    when(resolverMock.resolveChannelsByPredicate(DO_B, PREDICATE))
      .thenReturn(List.of(new CrossDoChannelResolver.ResolvedChannel(2L, "m", "d", "l", "symB", "f")));
    when(serviceMock.getDataPointsLttbOptimised(anyLong(), any(), anyLong(), anyLong(), anyInt()))
      .thenReturn(List.of());

    resource.getCrossDoBulkData(
      "timeseries",
      new CrossDoBulkDataRequestIO(List.of(DO_A, DO_B), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );

    verify(serviceMock, times(2))
      .getDataPointsLttbOptimised(anyLong(), any(Timeseries.class), eq(START_NS), eq(END_NS), eq(500));
  }
}
