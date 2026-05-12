package de.dlr.shepard.provenance.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProvenanceStatsServiceTest {

  @Mock
  ActivityDAO activityDAO;

  ProvenanceStatsService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new ProvenanceStatsService();
    service.activityDAO = activityDAO;
  }

  private static ActivityDAO.StatsSnapshot snap(long total, long distinctAgents, List<long[]> buckets, Map<String, Long> kinds) {
    var s = new ActivityDAO.StatsSnapshot();
    s.totalCount = total;
    s.distinctAgents = distinctAgents;
    s.buckets = buckets;
    s.totalsByActionKind = new java.util.LinkedHashMap<>(kinds);
    return s;
  }

  @Test
  void instanceScopeQueriesUnfilteredAggregation() {
    long now = 1_700_000_000_000L;
    long since = now - 30L * ProvenanceStatsService.DAY_MILLIS;
    var bucketList = List.of(new long[] { since, 5L }, new long[] { since + ProvenanceStatsService.DAY_MILLIS, 3L });
    when(activityDAO.aggregateStats(null, null, since, now, ProvenanceStatsService.DAY_MILLIS))
      .thenReturn(snap(8L, 2L, bucketList, Map.of("CREATE", 4L, "UPDATE", 4L)));

    var out = service.compute(ProvenanceStatsService.SCOPE_INSTANCE, null, since, now);

    assertEquals("instance", out.getScope());
    assertEquals(8L, out.getTotalCount());
    assertEquals(2L, out.getDistinctAgents());
    assertEquals(Map.of("CREATE", 4L, "UPDATE", 4L), out.getTotalsByActionKind());
    assertEquals(2, out.getBuckets().size());
    assertEquals(2, out.getCumulative().size());
    assertEquals(ProvenanceStatsService.DAY_MILLIS, out.getBucketMillis());
  }

  @Test
  void collectionScopeFiltersByTargetAppId() {
    long now = 1_700_000_000_000L;
    long since = now - 30L * ProvenanceStatsService.DAY_MILLIS;
    when(activityDAO.aggregateStats(any(), any(), anyLong(), anyLong(), anyLong())).thenReturn(snap(0L, 0L, List.of(), Map.of()));

    service.compute(ProvenanceStatsService.SCOPE_COLLECTION, "appid-1", since, now);

    verify(activityDAO).aggregateStats("appid-1", null, since, now, ProvenanceStatsService.DAY_MILLIS);
  }

  @Test
  void userScopeFiltersByAgentAndDistinctIsBoolean() {
    long now = 1_700_000_000_000L;
    long since = now - 30L * ProvenanceStatsService.DAY_MILLIS;
    when(activityDAO.aggregateStats(null, "alice", since, now, ProvenanceStatsService.DAY_MILLIS))
      .thenReturn(snap(7L, 99L /* should be ignored for user scope */, List.of(new long[] { since, 7L }), Map.of("CREATE", 7L)));

    var out = service.compute(ProvenanceStatsService.SCOPE_USER, "alice", since, now);

    assertEquals(7L, out.getTotalCount());
    assertEquals(1L, out.getDistinctAgents()); // user scope: 1 when active, ignores DAO's distinctAgents
  }

  @Test
  void userScopeWithNoActivityReportsZeroContributors() {
    long now = 1_700_000_000_000L;
    long since = now - 30L * ProvenanceStatsService.DAY_MILLIS;
    when(activityDAO.aggregateStats(null, "alice", since, now, ProvenanceStatsService.DAY_MILLIS))
      .thenReturn(snap(0L, 5L, List.of(), Map.of()));

    var out = service.compute(ProvenanceStatsService.SCOPE_USER, "alice", since, now);

    assertEquals(0L, out.getTotalCount());
    assertEquals(0L, out.getDistinctAgents());
  }

  @Test
  void windowLongerThan90DaysSwitchesToWeeklyBuckets() {
    long now = 1_700_000_000_000L;
    long since = now - 180L * ProvenanceStatsService.DAY_MILLIS;
    when(activityDAO.aggregateStats(null, null, since, now, ProvenanceStatsService.WEEK_MILLIS))
      .thenReturn(snap(0L, 0L, List.of(), Map.of()));

    var out = service.compute(ProvenanceStatsService.SCOPE_INSTANCE, null, since, now);

    assertEquals(ProvenanceStatsService.WEEK_MILLIS, out.getBucketMillis());
    verify(activityDAO).aggregateStats(null, null, since, now, ProvenanceStatsService.WEEK_MILLIS);
  }

  @Test
  void sinceAfterUntilThrows() {
    long now = 1_700_000_000_000L;
    assertThrows(IllegalArgumentException.class, () -> service.compute(ProvenanceStatsService.SCOPE_INSTANCE, null, now, now - 1));
  }

  @Test
  void unknownScopeThrows() {
    long now = 1_700_000_000_000L;
    long since = now - 30L * ProvenanceStatsService.DAY_MILLIS;
    assertThrows(IllegalArgumentException.class, () -> service.compute("bogus", "id", since, now));
  }

  @Test
  void cumulativeIntegralRunsAcrossBuckets() {
    long now = 1_700_000_000_000L;
    long since = now - 30L * ProvenanceStatsService.DAY_MILLIS;
    var bucketList = List.of(
      new long[] { since, 10L },
      new long[] { since + ProvenanceStatsService.DAY_MILLIS, 20L },
      new long[] { since + 2 * ProvenanceStatsService.DAY_MILLIS, 30L }
    );
    when(activityDAO.aggregateStats(any(), any(), anyLong(), anyLong(), anyLong())).thenReturn(snap(60L, 1L, bucketList, Map.of()));

    var out = service.compute(ProvenanceStatsService.SCOPE_INSTANCE, null, since, now);

    assertEquals(60L, out.getTotalCount());
    assertEquals(3, out.getCumulative().size());
    assertEquals(10L, out.getCumulative().get(0)[1]);
    assertEquals(30L, out.getCumulative().get(1)[1]);
    assertEquals(60L, out.getCumulative().get(2)[1]);
  }

  @Test
  void cumulativeIntegralHelperHandlesEmpty() {
    assertEquals(0, ProvenanceStatsService.cumulativeIntegral(List.of()).size());
  }

  @Test
  void cumulativeIntegralHelperKeepsBucketAlignment() {
    var input = List.of(new long[] { 100L, 5L }, new long[] { 200L, 7L });
    var out = ProvenanceStatsService.cumulativeIntegral(input);
    assertEquals(100L, out.get(0)[0]);
    assertEquals(5L, out.get(0)[1]);
    assertEquals(200L, out.get(1)[0]);
    assertEquals(12L, out.get(1)[1]);
  }

  @Test
  void thresholdConstantsSane() {
    assertEquals(86_400_000L, ProvenanceStatsService.DAY_MILLIS);
    assertTrue(ProvenanceStatsService.WEEK_MILLIS == 7L * ProvenanceStatsService.DAY_MILLIS);
  }
}
