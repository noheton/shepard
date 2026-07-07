package de.dlr.shepard.v2.collection.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
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
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    // Default: batch-resolve both collections (1 Cypher round-trip).
    when(entityIdResolver.resolveLongs(anyList()))
      .thenReturn(new LinkedHashMap<>(Map.of(COLL_A, OGM_A, COLL_B, OGM_B)));
    // Default: batch permission check allows both (1 Cypher round-trip).
    when(permissionsService.filterAllowedForUser(anyCollection(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(Set.of(OGM_A, OGM_B));
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
    Map<String, Long> resolved = new LinkedHashMap<>();
    List<String> atLimit = new java.util.ArrayList<>();
    for (int i = 0; i < CollectionCrossTimelineRest.MAX_COLLECTIONS; i++) {
      String id = "coll-appid-" + i;
      atLimit.add(id);
      resolved.put(id, (long) (200 + i));
    }
    when(entityIdResolver.resolveLongs(anyList())).thenReturn(resolved);
    when(permissionsService.filterAllowedForUser(anyCollection(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(new java.util.HashSet<>(resolved.values()));
    when(timelineDAO.aggregateMulti(atLimit))
      .thenReturn(new CollectionTimelineDAO.TimelineAggregate(List.of(), 0L, null, null));

    Response r = resource.crossTimeline(atLimit, 1, sc);
    assertThat(r.getStatus()).isEqualTo(200);
  }

  @Test
  void returns404WhenFirstCollectionNotFound() {
    // resolveLongs returns a map without COLL_A (not found).
    when(entityIdResolver.resolveLongs(anyList()))
      .thenReturn(new LinkedHashMap<>(Map.of(COLL_B, OGM_B)));

    Response r = resource.crossTimeline(List.of(COLL_A, COLL_B), 1, sc);

    assertThat(r.getStatus()).isEqualTo(404);
    verify(timelineDAO, never()).aggregateMulti(List.of(COLL_A, COLL_B));
  }

  @Test
  void returns404WhenSecondCollectionNotFound() {
    // resolveLongs returns a map without COLL_B (not found).
    when(entityIdResolver.resolveLongs(anyList()))
      .thenReturn(new LinkedHashMap<>(Map.of(COLL_A, OGM_A)));

    Response r = resource.crossTimeline(List.of(COLL_A, COLL_B), 1, sc);

    assertThat(r.getStatus()).isEqualTo(404);
    verify(timelineDAO, never()).aggregateMulti(List.of(COLL_A, COLL_B));
  }

  @Test
  void returns403WhenCallerLacksReadOnSecondCollection() {
    // filterAllowedForUser returns only OGM_A (COLL_B forbidden).
    when(permissionsService.filterAllowedForUser(anyCollection(), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(Set.of(OGM_A));

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
