package de.dlr.shepard.provenance.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
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

  @Test
  void instanceScopeQueriesUnfilteredAggregations() {
    long now = 1_700_000_000_000L;
    long since = now - 30L * ProvenanceStatsService.DAY_MILLIS;
    when(activityDAO.aggregateBuckets(null, null, since, now, ProvenanceStatsService.DAY_MILLIS))
      .thenReturn(List.of(new long[] { since, 5 }, new long[] { since + ProvenanceStatsService.DAY_MILLIS, 3 }));
    when(activityDAO.totalsByActionKind(null, null, since, now)).thenReturn(Map.of("CREATE", 4L, "UPDATE", 4L));
    when(activityDAO.distinctAgentCount(null, since, now)).thenReturn(2L);

    var out = service.compute(ProvenanceStatsService.SCOPE_INSTANCE, null, since, now);

    assertEquals("instance", out.getScope());
    assertEquals(8L, out.getTotalCount());
    assertEquals(2L, out.getDistinctAgents());
    assertEquals(Map.of("CREATE", 4L, "UPDATE", 4L), out.getTotalsByActionKind());
    assertEquals(2, out.getBuckets().size());
    assertEquals(ProvenanceStatsService.DAY_MILLIS, out.getBucketMillis());
  }

  @Test
  void collectionScopeFiltersByTargetAppId() {
    long now = 1_700_000_000_000L;
    long since = now - 30L * ProvenanceStatsService.DAY_MILLIS;
    when(activityDAO.aggregateBuckets(any(), any(), anyLong(), anyLong(), anyLong())).thenReturn(List.of());
    when(activityDAO.totalsByActionKind(any(), any(), anyLong(), anyLong())).thenReturn(Map.of());
    when(activityDAO.distinctAgentCount(any(), anyLong(), anyLong())).thenReturn(0L);

    service.compute(ProvenanceStatsService.SCOPE_COLLECTION, "appid-1", since, now);

    verify(activityDAO).aggregateBuckets("appid-1", null, since, now, ProvenanceStatsService.DAY_MILLIS);
    verify(activityDAO).totalsByActionKind("appid-1", null, since, now);
    verify(activityDAO).distinctAgentCount("appid-1", since, now);
  }

  @Test
  void userScopeFiltersByAgentAndDistinctIsBoolean() {
    long now = 1_700_000_000_000L;
    long since = now - 30L * ProvenanceStatsService.DAY_MILLIS;
    when(activityDAO.aggregateBuckets(null, "alice", since, now, ProvenanceStatsService.DAY_MILLIS))
      .thenReturn(List.of(new long[] { since, 7 }));
    when(activityDAO.totalsByActionKind(null, "alice", since, now)).thenReturn(Map.of("CREATE", 7L));

    var out = service.compute(ProvenanceStatsService.SCOPE_USER, "alice", since, now);

    assertEquals(7L, out.getTotalCount());
    assertEquals(1L, out.getDistinctAgents()); // user scope: 1 when active
    verify(activityDAO, never()).distinctAgentCount(any(), anyLong(), anyLong());
  }

  @Test
  void userScopeWithNoActivityReportsZeroContributors() {
    long now = 1_700_000_000_000L;
    long since = now - 30L * ProvenanceStatsService.DAY_MILLIS;
    when(activityDAO.aggregateBuckets(null, "alice", since, now, ProvenanceStatsService.DAY_MILLIS)).thenReturn(List.of());
    when(activityDAO.totalsByActionKind(null, "alice", since, now)).thenReturn(Map.of());

    var out = service.compute(ProvenanceStatsService.SCOPE_USER, "alice", since, now);

    assertEquals(0L, out.getTotalCount());
    assertEquals(0L, out.getDistinctAgents());
  }

  @Test
  void windowLongerThan90DaysSwitchesToWeeklyBuckets() {
    long now = 1_700_000_000_000L;
    long since = now - 180L * ProvenanceStatsService.DAY_MILLIS;
    when(activityDAO.aggregateBuckets(null, null, since, now, ProvenanceStatsService.WEEK_MILLIS)).thenReturn(List.of());
    when(activityDAO.totalsByActionKind(null, null, since, now)).thenReturn(Map.of());
    when(activityDAO.distinctAgentCount(null, since, now)).thenReturn(0L);

    var out = service.compute(ProvenanceStatsService.SCOPE_INSTANCE, null, since, now);

    assertEquals(ProvenanceStatsService.WEEK_MILLIS, out.getBucketMillis());
    verify(activityDAO).aggregateBuckets(null, null, since, now, ProvenanceStatsService.WEEK_MILLIS);
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
  void totalCountIsSumOfBuckets() {
    long now = 1_700_000_000_000L;
    long since = now - 30L * ProvenanceStatsService.DAY_MILLIS;
    when(activityDAO.aggregateBuckets(any(), any(), anyLong(), anyLong(), anyLong()))
      .thenReturn(List.of(new long[] { since, 10 }, new long[] { since + ProvenanceStatsService.DAY_MILLIS, 20 }, new long[] { since + 2 * ProvenanceStatsService.DAY_MILLIS, 30 }));
    when(activityDAO.totalsByActionKind(any(), any(), anyLong(), anyLong())).thenReturn(Map.of());
    when(activityDAO.distinctAgentCount(any(), anyLong(), anyLong())).thenReturn(0L);

    var out = service.compute(ProvenanceStatsService.SCOPE_INSTANCE, null, since, now);
    assertEquals(60L, out.getTotalCount());
  }

  @Test
  void thresholdConstantsSane() {
    assertEquals(86_400_000L, ProvenanceStatsService.DAY_MILLIS);
    assertTrue(ProvenanceStatsService.WEEK_MILLIS == 7L * ProvenanceStatsService.DAY_MILLIS);
  }
}
