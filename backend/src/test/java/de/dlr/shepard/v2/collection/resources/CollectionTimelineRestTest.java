package de.dlr.shepard.v2.collection.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.collection.daos.CollectionTimelineDAO;
import de.dlr.shepard.v2.collection.daos.CollectionTimelineDAO.TimelineAggregate;
import de.dlr.shepard.v2.collection.daos.CollectionTimelineDAO.TimelineRow;
import de.dlr.shepard.v2.collection.io.CollectionTimelineIO;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * COLL-TIMELINE-1 — resource-layer tests for {@link CollectionTimelineRest}.
 *
 * <p>Mocks the DAO + permissions; no Neo4j contact. Covers the eight
 * gates required by the GAP-8 spec:
 * <ol>
 *   <li>Empty Collection</li>
 *   <li>Collection with mixed-process DOs</li>
 *   <li>DOs without process-type annotation → unclassified lane</li>
 *   <li>Permission gate (401, 403, 404)</li>
 *   <li>Bin-size auto-coarsening when range &gt; 730 days</li>
 *   <li>Status counts (NCR / REJECTED math)</li>
 *   <li>Performance smoke (1000 mock rows, &lt; 2 s)</li>
 *   <li>Cache header set</li>
 * </ol>
 */
class CollectionTimelineRestTest {

  static final String COLL_APP_ID = "coll-appid-1";
  static final long   COLL_OGM_ID = 42L;
  static final String CALLER      = "alice";

  @Mock CollectionTimelineDAO timelineDAO;
  @Mock PermissionsService permissionsService;
  @Mock EntityIdResolver entityIdResolver;
  @Mock SecurityContext sc;
  @Mock Principal principal;

  CollectionTimelineRest resource;

  private static long day(String iso) {
    return LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionTimelineRest();
    resource.timelineDAO = timelineDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenReturn(COLL_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
  }

  @Test
  void emptyCollectionReturnsEmptyEnvelopeWithCacheHeader() {
    when(timelineDAO.aggregate(COLL_APP_ID))
      .thenReturn(new TimelineAggregate(List.of(), 0L, null, null));

    Response r = resource.timeline(COLL_APP_ID, 1, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(r.getHeaderString("Cache-Control")).contains("max-age=300");
    CollectionTimelineIO body = (CollectionTimelineIO) r.getEntity();
    assertThat(body.getLanes()).isEmpty();
    assertThat(body.getTotalDataObjects()).isEqualTo(0L);
    assertThat(body.getRangeStart()).isNull();
  }

  @Test
  void mixedProcessRowsRenderAsMultipleLanes() {
    var rows = List.of(
      new TimelineRow("AFP Layup",          day("2024-01-01"), 3, 0, 0),
      new TimelineRow("Ultrasonic Welding", day("2024-01-05"), 2, 0, 0),
      new TimelineRow("NDT Inspection",     day("2024-01-10"), 1, 0, 0)
    );
    when(timelineDAO.aggregate(COLL_APP_ID))
      .thenReturn(new TimelineAggregate(rows, 6L, day("2024-01-01"), day("2024-01-10")));

    Response r = resource.timeline(COLL_APP_ID, 1, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    CollectionTimelineIO body = (CollectionTimelineIO) r.getEntity();
    assertThat(body.getLanes()).hasSize(3);
    assertThat(body.getLanes().get(0).getKey()).isEqualTo("afp-layup");
    assertThat(body.getTotalDataObjects()).isEqualTo(6L);
  }

  @Test
  void unclassifiedDataObjectsCollectIntoUnclassifiedLane() {
    var rows = List.of(
      new TimelineRow(CollectionTimelineDAO.UNCLASSIFIED_LANE_KEY, day("2024-02-01"), 4, 0, 0)
    );
    when(timelineDAO.aggregate(COLL_APP_ID))
      .thenReturn(new TimelineAggregate(rows, 4L, day("2024-02-01"), day("2024-02-01")));

    Response r = resource.timeline(COLL_APP_ID, 1, sc);
    CollectionTimelineIO body = (CollectionTimelineIO) r.getEntity();
    assertThat(body.getLanes()).hasSize(1);
    assertThat(body.getLanes().get(0).getKey()).isEqualTo("unclassified");
    assertThat(body.getLanes().get(0).getLabel()).isEqualTo("Unclassified");
  }

  @Test
  void returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.timeline(COLL_APP_ID, 1, sc);
    assertThat(r.getStatus()).isEqualTo(401);
    verify(timelineDAO, never()).aggregate(COLL_APP_ID);
  }

  @Test
  void returns404WhenCollectionNotFound() {
    when(entityIdResolver.resolveLong(COLL_APP_ID)).thenThrow(new NotFoundException());
    Response r = resource.timeline(COLL_APP_ID, 1, sc);
    assertThat(r.getStatus()).isEqualTo(404);
    verify(timelineDAO, never()).aggregate(COLL_APP_ID);
  }

  @Test
  void returns403WhenCallerLacksReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);
    Response r = resource.timeline(COLL_APP_ID, 1, sc);
    assertThat(r.getStatus()).isEqualTo(403);
    verify(timelineDAO, never()).aggregate(COLL_APP_ID);
  }

  @Test
  void autoCoarsensBinSizeWhenSpanExceedsCap() {
    // 1000 days × daily bins would produce 1000 bins per lane → cap hit.
    var rows = List.of(
      new TimelineRow("AFP Layup", day("2023-01-01"), 1, 0, 0),
      new TimelineRow("AFP Layup", day("2025-09-28"), 1, 0, 0)
    );
    when(timelineDAO.aggregate(COLL_APP_ID))
      .thenReturn(new TimelineAggregate(rows, 2L, day("2023-01-01"), day("2025-09-28")));

    Response r = resource.timeline(COLL_APP_ID, 1, sc);
    CollectionTimelineIO body = (CollectionTimelineIO) r.getEntity();
    assertThat(body.getBinSizeDays()).isGreaterThanOrEqualTo(7);
  }

  @Test
  void statusCountsArePropagatedFromRowsToBins() {
    var rows = List.of(
      new TimelineRow("NDT Inspection", day("2024-03-15"), 10, 3, 1)
    );
    when(timelineDAO.aggregate(COLL_APP_ID))
      .thenReturn(new TimelineAggregate(rows, 10L, day("2024-03-15"), day("2024-03-15")));

    Response r = resource.timeline(COLL_APP_ID, 1, sc);
    CollectionTimelineIO body = (CollectionTimelineIO) r.getEntity();
    var bin = body.getLanes().get(0).getBins().get(0);
    assertThat(bin.getCount()).isEqualTo(10);
    assertThat(bin.getNcrCount()).isEqualTo(3);
    assertThat(bin.getRejectCount()).isEqualTo(1);
  }

  @Test
  void thousandRowMockBenchmarkCompletesWellUnderTwoSeconds() {
    // 1000 rows simulating MFFD-scale across 5 lanes, 200 days each.
    String[] lanes = { "AFP Layup", "Ultrasonic Welding", "Resistance Welding", "NDT Inspection", "Frame Welding" };
    List<TimelineRow> rows = new ArrayList<>(1000);
    long base = day("2024-01-01");
    long max = base;
    for (int i = 0; i < 1000; i++) {
      long d = base + (i % 200) * 86_400_000L;
      if (d > max) max = d;
      rows.add(new TimelineRow(lanes[i % lanes.length], d, 1, (i % 50 == 0) ? 1 : 0, 0));
    }
    when(timelineDAO.aggregate(COLL_APP_ID))
      .thenReturn(new TimelineAggregate(rows, 1000L, base, max));

    long t0 = System.nanoTime();
    Response r = resource.timeline(COLL_APP_ID, 1, sc);
    long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(elapsedMs).isLessThan(2000L);
    CollectionTimelineIO body = (CollectionTimelineIO) r.getEntity();
    assertThat(body.getLanes()).hasSize(5);
    assertThat(body.getTotalDataObjects()).isEqualTo(1000L);
  }

  @Test
  void cacheHeaderSetTo300SecondsMustRevalidate() {
    when(timelineDAO.aggregate(COLL_APP_ID))
      .thenReturn(new TimelineAggregate(List.of(), 0L, null, null));
    Response r = resource.timeline(COLL_APP_ID, 1, sc);
    assertThat(r.getHeaderString("Cache-Control")).isEqualTo("max-age=300, must-revalidate");
  }
}
