package de.dlr.shepard.v2.collection.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * COLL-TIMELINE-CROSS-1 — resource-layer tests for
 * {@link CollectionCrossTimelineRest}.
 *
 * <p>Mocks DAO + permissions; no Neo4j contact. Covers:
 * <ul>
 *   <li>Happy path — two Collections, merged swimlanes</li>
 *   <li>Empty result when both Collections are empty</li>
 *   <li>Auth gates: 401, 403 (one inaccessible), 404 (one missing)</li>
 *   <li>400 when no collections param supplied</li>
 *   <li>Comma-separated single param normalised correctly</li>
 *   <li>Cache header set</li>
 *   <li>{@link CollectionCrossTimelineRest#parseIds} unit tests</li>
 * </ul>
 */
class CollectionCrossTimelineRestTest {

  static final String COLL_A   = "coll-appid-a";
  static final String COLL_B   = "coll-appid-b";
  static final long   OGM_A    = 101L;
  static final long   OGM_B    = 102L;
  static final String CALLER   = "alice";

  @Mock CollectionTimelineDAO timelineDAO;
  @Mock PermissionsService permissionsService;
  @Mock EntityIdResolver entityIdResolver;
  @Mock SecurityContext sc;
  @Mock Principal principal;

  CollectionCrossTimelineRest resource;

  private static long day(String iso) {
    return LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionCrossTimelineRest();
    resource.timelineDAO = timelineDAO;
    resource.permissionsService = permissionsService;
    resource.entityIdResolver = entityIdResolver;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(entityIdResolver.resolveLong(COLL_A)).thenReturn(OGM_A);
    when(entityIdResolver.resolveLong(COLL_B)).thenReturn(OGM_B);
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_A), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_B), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
  }

  @Test
  void twoCollectionsReturnsMergedEnvelopeWith200() {
    var rows = List.of(
      new TimelineRow("AFP Layup",          day("2024-01-01"), 3, 0, 0),
      new TimelineRow("Ultrasonic Welding", day("2024-01-05"), 2, 1, 0)
    );
    when(timelineDAO.aggregateMulti(List.of(COLL_A, COLL_B)))
      .thenReturn(new TimelineAggregate(rows, 5L, day("2024-01-01"), day("2024-01-05")));

    Response r = resource.crossTimeline(List.of(COLL_A, COLL_B), 1, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    CollectionTimelineIO body = (CollectionTimelineIO) r.getEntity();
    assertThat(body.getTotalDataObjects()).isEqualTo(5L);
    assertThat(body.getLanes()).hasSize(2);
    assertThat(body.getLanes().get(0).getKey()).isEqualTo("afp-layup");
  }

  @Test
  void emptyCombinedCollectionsReturnsEmptyEnvelopeWith200() {
    when(timelineDAO.aggregateMulti(List.of(COLL_A, COLL_B)))
      .thenReturn(new TimelineAggregate(List.of(), 0L, null, null));

    Response r = resource.crossTimeline(List.of(COLL_A, COLL_B), 1, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    CollectionTimelineIO body = (CollectionTimelineIO) r.getEntity();
    assertThat(body.getLanes()).isEmpty();
    assertThat(body.getTotalDataObjects()).isEqualTo(0L);
  }

  @Test
  void cacheHeaderSetTo300SecondsMustRevalidate() {
    when(timelineDAO.aggregateMulti(List.of(COLL_A, COLL_B)))
      .thenReturn(new TimelineAggregate(List.of(), 0L, null, null));

    Response r = resource.crossTimeline(List.of(COLL_A, COLL_B), 1, sc);

    assertThat(r.getHeaderString("Cache-Control")).isEqualTo("max-age=300, must-revalidate");
  }

  @Test
  void returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);

    Response r = resource.crossTimeline(List.of(COLL_A, COLL_B), 1, sc);

    assertThat(r.getStatus()).isEqualTo(401);
    verify(timelineDAO, never()).aggregateMulti(List.of(COLL_A, COLL_B));
  }

  @Test
  void returns400WhenNoCollectionsParam() {
    Response r = resource.crossTimeline(List.of(), 1, sc);
    assertThat(r.getStatus()).isEqualTo(400);
  }

  @Test
  void returns400WhenNullCollectionsParam() {
    Response r = resource.crossTimeline(null, 1, sc);
    assertThat(r.getStatus()).isEqualTo(400);
  }

  @Test
  void returns400WhenMoreThanMaxCollectionsSupplied() {
    List<String> tooMany = new java.util.ArrayList<>();
    for (int i = 0; i < CollectionCrossTimelineRest.MAX_COLLECTIONS + 1; i++) {
      tooMany.add("coll-appid-" + i);
    }
    Response r = resource.crossTimeline(tooMany, 1, sc);
    assertThat(r.getStatus()).isEqualTo(400);
    verify(timelineDAO, never()).aggregateMulti(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void returns200WithExactlyMaxCollections() {
    List<String> atLimit = new java.util.ArrayList<>();
    for (int i = 0; i < CollectionCrossTimelineRest.MAX_COLLECTIONS; i++) {
      String id = "coll-appid-" + i;
      atLimit.add(id);
      when(entityIdResolver.resolveLong(id)).thenReturn((long) (200 + i));
      when(permissionsService.isAccessTypeAllowedForUser(
          eq((long) (200 + i)), eq(AccessType.Read), eq(CALLER), anyLong()))
        .thenReturn(true);
    }
    when(timelineDAO.aggregateMulti(atLimit))
      .thenReturn(new CollectionTimelineDAO.TimelineAggregate(List.of(), 0L, null, null));

    Response r = resource.crossTimeline(atLimit, 1, sc);
    assertThat(r.getStatus()).isEqualTo(200);
  }

  @Test
  void returns404WhenFirstCollectionNotFound() {
    when(entityIdResolver.resolveLong(COLL_A)).thenThrow(new NotFoundException());

    Response r = resource.crossTimeline(List.of(COLL_A, COLL_B), 1, sc);

    assertThat(r.getStatus()).isEqualTo(404);
    verify(timelineDAO, never()).aggregateMulti(List.of(COLL_A, COLL_B));
  }

  @Test
  void returns404WhenSecondCollectionNotFound() {
    when(entityIdResolver.resolveLong(COLL_B)).thenThrow(new NotFoundException());

    Response r = resource.crossTimeline(List.of(COLL_A, COLL_B), 1, sc);

    assertThat(r.getStatus()).isEqualTo(404);
    verify(timelineDAO, never()).aggregateMulti(List.of(COLL_A, COLL_B));
  }

  @Test
  void returns403WhenCallerLacksReadOnSecondCollection() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_B), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);

    Response r = resource.crossTimeline(List.of(COLL_A, COLL_B), 1, sc);

    assertThat(r.getStatus()).isEqualTo(403);
    verify(timelineDAO, never()).aggregateMulti(List.of(COLL_A, COLL_B));
  }

  // ── parseIds unit tests ──────────────────────────────────────────────────

  @Test
  void parseIds_multiValueParams_expandsToList() {
    List<String> result = CollectionCrossTimelineRest.parseIds(List.of(COLL_A, COLL_B));
    assertEquals(List.of(COLL_A, COLL_B), result);
  }

  @Test
  void parseIds_csvSingleParam_expandsToList() {
    List<String> result = CollectionCrossTimelineRest.parseIds(List.of(COLL_A + "," + COLL_B));
    assertEquals(List.of(COLL_A, COLL_B), result);
  }

  @Test
  void parseIds_trimsWhitespace() {
    List<String> result = CollectionCrossTimelineRest.parseIds(List.of("  " + COLL_A + "  ,  " + COLL_B));
    assertEquals(List.of(COLL_A, COLL_B), result);
  }

  @Test
  void parseIds_nullOrEmpty_returnsEmptyList() {
    assertThat(CollectionCrossTimelineRest.parseIds(null)).isEmpty();
    assertThat(CollectionCrossTimelineRest.parseIds(List.of())).isEmpty();
  }

  @Test
  void parseIds_blankEntries_skipped() {
    List<String> result = CollectionCrossTimelineRest.parseIds(List.of("  ", COLL_A, ",,"));
    assertEquals(List.of(COLL_A), result);
  }
}
