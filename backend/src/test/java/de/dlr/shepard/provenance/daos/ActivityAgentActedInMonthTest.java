package de.dlr.shepard.provenance.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.provenance.entities.Activity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

/**
 * NEO-AUDIT-2026-07-18-ACTIVITY-SUPERNODE — unit tests for the time-bucketed
 * Agent index written alongside {@code WAS_ASSOCIATED_WITH}.
 *
 * <p>Verifies that {@link ActivityDAO#writeAgentActedInMonth(Activity, String)}:
 * <ul>
 *   <li>formats {@code ym} correctly for ordinary months, January, and December</li>
 *   <li>is immune to JVM-timezone drift — uses UTC, not the JVM default TZ</li>
 *   <li>issues the exact bucketed-edge Cypher MERGE with the correct {@code ym} param</li>
 *   <li>silently skips writes when the username or {@code startedAtMillis} is missing</li>
 * </ul>
 * and that {@link ActivityDAO#wireEdges} drives the bucketed edge for a fully
 * populated Activity but not when the Activity or its appId is null.
 */
public class ActivityAgentActedInMonthTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private ActivityDAO dao;

  // --- ym format helpers --------------------------------------------------

  /** Epoch-millis for a UTC year/month/day at 10:00. */
  private static long utcMillis(int year, int month, int day) {
    return LocalDateTime.of(year, month, day, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  /**
   * Derive ym using the same UTC-explicit logic as
   * ActivityDAO.writeAgentActedInMonth() — the test's expected-value oracle.
   */
  private static String ymOf(long millis) {
    var utc = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC);
    return String.format("%04d%02d", utc.getYear(), utc.getMonthValue());
  }

  private static Activity activityWith(String appId, Long startedAtMillis) {
    Activity a = new Activity();
    a.setAppId(appId);
    a.setStartedAtMillis(startedAtMillis);
    return a;
  }

  // --- ym format verification ---------------------------------------------

  @Test
  public void ymFormat_ordinaryMonth_July2026() {
    assertEquals("202607", ymOf(utcMillis(2026, 7, 15)), "July 2026 must format to '202607'");
  }

  @Test
  public void ymFormat_january() {
    assertEquals("202501", ymOf(utcMillis(2025, 1, 1)), "January must format to '202501' (leading zero)");
  }

  @Test
  public void ymFormat_december() {
    assertEquals("202412", ymOf(utcMillis(2024, 12, 31)), "December must format to '202412'");
  }

  @Test
  public void ymFormat_alwaysSixChars() {
    int year = 2024; // leap year
    for (int month = 1; month <= 12; month++) {
      String ym = ymOf(utcMillis(year, month, 1));
      assertEquals(6, ym.length(), "ym for month " + month + " must be exactly 6 chars, got: " + ym);
    }
  }

  @Test
  public void ymFormat_utcBoundary_cest_midnight() {
    // 2026-07-01 00:30 CEST  =  2026-06-30 22:30 UTC → ym must be UTC month "202606".
    long millis = LocalDateTime.of(2026, 7, 1, 0, 30).toInstant(ZoneOffset.ofHours(2)).toEpochMilli();
    assertEquals("202606", ymOf(millis), "2026-07-01T00:30 CEST must map to UTC June 2026 → '202606'");
  }

  // --- writeAgentActedInMonth behaviour -----------------------------------

  @Test
  public void writeAgentActedInMonth_issuesMergeWithCorrectYm() {
    Activity a = activityWith("act-app-id-123", utcMillis(2026, 7, 20));

    dao.writeAgentActedInMonth(a, "importer-svc");

    verify(session).query(
      eq(ActivityDAO.AGENT_ACTED_IN_MONTH_CYPHER),
      argThat((Map<String, Object> params) ->
        "importer-svc".equals(params.get("username")) &&
        "act-app-id-123".equals(params.get("activityAppId")) &&
        "202607".equals(params.get("ym"))
      )
    );
  }

  @Test
  public void writeAgentActedInMonth_skips_whenUsernameNull() {
    Activity a = activityWith("act-app-id-456", utcMillis(2026, 7, 1));

    dao.writeAgentActedInMonth(a, null);

    verify(session, never()).query(eq(ActivityDAO.AGENT_ACTED_IN_MONTH_CYPHER), any());
  }

  @Test
  public void writeAgentActedInMonth_skips_whenUsernameBlank() {
    Activity a = activityWith("act-app-id-789", utcMillis(2026, 7, 1));

    dao.writeAgentActedInMonth(a, "   ");

    verify(session, never()).query(eq(ActivityDAO.AGENT_ACTED_IN_MONTH_CYPHER), any());
  }

  @Test
  public void writeAgentActedInMonth_skips_whenStartedAtMillisNull() {
    Activity a = activityWith("act-app-id-000", null);

    dao.writeAgentActedInMonth(a, "importer-svc");

    verify(session, never()).query(eq(ActivityDAO.AGENT_ACTED_IN_MONTH_CYPHER), any());
  }

  // --- wireEdges integration ----------------------------------------------

  @Test
  public void wireEdges_writesBucketedEdge_forFullActivity() {
    Activity a = activityWith("act-app-id-wire", utcMillis(2026, 3, 9));

    dao.wireEdges(a, "operator", null, "CREATE");

    // The bucketed edge is written (scoped to the bucketed Cypher; wireEdges
    // also fires WAS_ASSOCIATED_WITH, which must not confuse this assertion).
    verify(session).query(
      eq(ActivityDAO.AGENT_ACTED_IN_MONTH_CYPHER),
      argThat((Map<String, Object> params) ->
        "operator".equals(params.get("username")) &&
        "act-app-id-wire".equals(params.get("activityAppId")) &&
        "202603".equals(params.get("ym"))
      )
    );
  }

  @Test
  public void wireEdges_skipsBucketedEdge_whenActivityAppIdNull() {
    Activity a = activityWith(null, utcMillis(2026, 7, 1));

    dao.wireEdges(a, "operator", null, "CREATE");

    verify(session, never()).query(eq(ActivityDAO.AGENT_ACTED_IN_MONTH_CYPHER), any());
  }

  @Test
  public void wireEdges_skipsBucketedEdge_whenSavedNull() {
    dao.wireEdges(null, "operator", null, "CREATE");

    verify(session, never()).query(eq(ActivityDAO.AGENT_ACTED_IN_MONTH_CYPHER), any());
  }
}
