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
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.timeseries.io.CrossDoBulkDataRequestIO;
import de.dlr.shepard.v2.timeseries.io.CrossDoSeriesIO;
import de.dlr.shepard.v2.timeseries.services.CrossDoChannelResolver;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TS-CROSS-DO-VIEW-1 — unit coverage for
 * {@code POST /v2/data-objects/cross-timeseries-bulk}.
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

  // ── Auth: unauthenticated caller is rejected ──────────────────────────────

  @Test
  void unauthenticatedCaller_returns401() {
    SecurityContext anon = mock(SecurityContext.class);
    when(anon.getUserPrincipal()).thenReturn(null);

    Response resp = resource.getCrossDoBulkData(
      new CrossDoBulkDataRequestIO(List.of(DO_A), PREDICATE, START_NS, END_NS, 500),
      anon
    );

    assertEquals(401, resp.getStatus());
    verify(permsMock, never()).isAccessAllowedForDataObjectAppId(any(), any(), any());
  }

  // ── Happy path: DO has matching channel, returns LTTB-downsampled series ──

  @Test
  void happyPath_returnsSeriesForResolvedChannel() {
    when(permsMock.isAccessAllowedForDataObjectAppId(eq(DO_A), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    DataObject doA = new DataObject();
    doA.setName("Track_244__Run_30239");
    when(daoMock.findByAppId(DO_A)).thenReturn(doA);
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
      new CrossDoBulkDataRequestIO(List.of(DO_A), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<CrossDoSeriesIO> body = (List<CrossDoSeriesIO>) resp.getEntity();
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
    when(permsMock.isAccessAllowedForDataObjectAppId(eq(DO_B), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    DataObject doB = new DataObject();
    doB.setName("Track_245");
    when(daoMock.findByAppId(DO_B)).thenReturn(doB);
    when(resolverMock.resolveChannelsByPredicate(DO_B, PREDICATE)).thenReturn(List.of());

    Response resp = resource.getCrossDoBulkData(
      new CrossDoBulkDataRequestIO(List.of(DO_B), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<CrossDoSeriesIO> body = (List<CrossDoSeriesIO>) resp.getEntity();
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
    when(permsMock.isAccessAllowedForDataObjectAppId(eq(DO_FORBIDDEN), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    when(permsMock.isAccessAllowedForDataObjectAppId(eq(DO_A), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    DataObject doA = new DataObject();
    doA.setName("ok");
    when(daoMock.findByAppId(DO_A)).thenReturn(doA);
    when(resolverMock.resolveChannelsByPredicate(DO_A, PREDICATE)).thenReturn(List.of());

    Response resp = resource.getCrossDoBulkData(
      new CrossDoBulkDataRequestIO(List.of(DO_FORBIDDEN, DO_A), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<CrossDoSeriesIO> body = (List<CrossDoSeriesIO>) resp.getEntity();
    assertEquals(1, body.size(), "forbidden DO must be dropped silently, allowed DO kept");
    assertEquals(DO_A, body.get(0).dataObjectAppId());
    // Crucially we never even hit the DAO for the forbidden DO.
    verify(daoMock, never()).findByAppId(DO_FORBIDDEN);
  }

  // ── Unknown DO appId: row with empty points + null name (signals 'lost') ──

  @Test
  void unknownAppId_emitsRowWithNullName() {
    when(permsMock.isAccessAllowedForDataObjectAppId(eq(DO_UNKNOWN), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(daoMock.findByAppId(DO_UNKNOWN)).thenReturn(null);

    Response resp = resource.getCrossDoBulkData(
      new CrossDoBulkDataRequestIO(List.of(DO_UNKNOWN), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<CrossDoSeriesIO> body = (List<CrossDoSeriesIO>) resp.getEntity();
    assertEquals(1, body.size());
    assertNull(body.get(0).dataObjectName());
    assertTrue(body.get(0).points().isEmpty());
    // Resolver and service not consulted for missing DO.
    verify(resolverMock, never()).resolveChannelsByPredicate(eq(DO_UNKNOWN), any());
    verify(serviceMock, never()).getDataPointsLttbOptimised(anyLong(), any(), anyLong(), anyLong(), anyInt());
  }

  // ── Mix of one allowed + one no-channel + one forbidden + one unknown ────

  @Test
  void mixedShapes_orderPreserved() {
    // Allowed + has data
    when(permsMock.isAccessAllowedForDataObjectAppId(eq(DO_A), eq(AccessType.Read), eq(CALLER))).thenReturn(true);
    DataObject doA = new DataObject(); doA.setName("A");
    when(daoMock.findByAppId(DO_A)).thenReturn(doA);
    when(resolverMock.resolveChannelsByPredicate(DO_A, PREDICATE))
      .thenReturn(List.of(new CrossDoChannelResolver.ResolvedChannel(
        1L, "m", "d", "l", "sym", "f"
      )));
    when(serviceMock.getDataPointsLttbOptimised(eq(1L), any(), anyLong(), anyLong(), anyInt()))
      .thenReturn(List.of(new TimeseriesDataPoint(1_000_000_000L, 1.0)));

    // Allowed + no channel
    when(permsMock.isAccessAllowedForDataObjectAppId(eq(DO_B), eq(AccessType.Read), eq(CALLER))).thenReturn(true);
    DataObject doB = new DataObject(); doB.setName("B");
    when(daoMock.findByAppId(DO_B)).thenReturn(doB);
    when(resolverMock.resolveChannelsByPredicate(DO_B, PREDICATE)).thenReturn(List.of());

    // Forbidden — drop
    when(permsMock.isAccessAllowedForDataObjectAppId(eq(DO_FORBIDDEN), eq(AccessType.Read), eq(CALLER))).thenReturn(false);

    // Allowed but unknown
    when(permsMock.isAccessAllowedForDataObjectAppId(eq(DO_C), eq(AccessType.Read), eq(CALLER))).thenReturn(true);
    when(daoMock.findByAppId(DO_C)).thenReturn(null);

    Response resp = resource.getCrossDoBulkData(
      new CrossDoBulkDataRequestIO(
        List.of(DO_A, DO_B, DO_FORBIDDEN, DO_C),
        PREDICATE, START_NS, END_NS, 500
      ),
      securityContext
    );

    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    List<CrossDoSeriesIO> body = (List<CrossDoSeriesIO>) resp.getEntity();
    // 3 rows: A (data), B (no channel), C (unknown). Forbidden dropped.
    assertEquals(3, body.size());
    assertEquals(DO_A, body.get(0).dataObjectAppId());
    assertEquals(DO_B, body.get(1).dataObjectAppId());
    assertEquals(DO_C, body.get(2).dataObjectAppId());
    assertNotNull(body.get(0).channelSymbolicName());
    assertNull(body.get(1).channelSymbolicName());
    assertNull(body.get(2).channelSymbolicName());
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
    when(permsMock.isAccessAllowedForDataObjectAppId(any(), eq(AccessType.Read), eq(CALLER))).thenReturn(true);

    DataObject doA = new DataObject(); doA.setName("A");
    DataObject doB = new DataObject(); doB.setName("B");
    when(daoMock.findByAppId(DO_A)).thenReturn(doA);
    when(daoMock.findByAppId(DO_B)).thenReturn(doB);
    when(resolverMock.resolveChannelsByPredicate(DO_A, PREDICATE))
      .thenReturn(List.of(new CrossDoChannelResolver.ResolvedChannel(1L, "m", "d", "l", "symA", "f")));
    when(resolverMock.resolveChannelsByPredicate(DO_B, PREDICATE))
      .thenReturn(List.of(new CrossDoChannelResolver.ResolvedChannel(2L, "m", "d", "l", "symB", "f")));
    when(serviceMock.getDataPointsLttbOptimised(anyLong(), any(), anyLong(), anyLong(), anyInt()))
      .thenReturn(List.of());

    resource.getCrossDoBulkData(
      new CrossDoBulkDataRequestIO(List.of(DO_A, DO_B), PREDICATE, START_NS, END_NS, 500),
      securityContext
    );

    verify(serviceMock, times(2))
      .getDataPointsLttbOptimised(anyLong(), any(Timeseries.class), eq(START_NS), eq(END_NS), eq(500));
  }
}
