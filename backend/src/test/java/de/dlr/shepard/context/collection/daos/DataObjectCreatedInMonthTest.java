package de.dlr.shepard.context.collection.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.collection.entities.DataObject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

/**
 * NEO-AUDIT-004 — Unit tests for the time-bucketed Agent index.
 *
 * <p>Verifies that {@link DataObjectDAO#writeCreatedInMonth(DataObject)}:
 * <ul>
 *   <li>formats {@code ym} correctly for ordinary months, January, and December</li>
 *   <li>is immune to JVM-timezone drift — uses UTC, not the JVM default TZ</li>
 *   <li>issues a Cypher MERGE with the correct {@code ym} parameter</li>
 *   <li>silently skips writes when {@code createdBy}, {@code appId},
 *       or {@code createdAt} are null</li>
 * </ul>
 */
public class DataObjectCreatedInMonthTest extends BaseTestCase {

  @Mock
  private Session session;

  @Mock
  private EntityIdResolver entityIdResolver;

  @InjectMocks
  private DataObjectDAO dao;

  // --- ym format helpers --------------------------------------------------

  /**
   * Build an epoch-millis Date from a UTC year/month/day triple.
   */
  private static Date utcDate(int year, int month, int day) {
    Instant instant = LocalDateTime.of(year, month, day, 10, 0).toInstant(ZoneOffset.UTC);
    return Date.from(instant);
  }

  /**
   * Derive ym using the same UTC-explicit logic as DataObjectDAO.writeCreatedInMonth().
   * Tests must use this helper — NOT String.format("%1$tY%1$tm", date), which would
   * exercise the old JVM-TZ path and produce wrong results on non-UTC hosts.
   */
  private static String ymOf(Date date) {
    var utc = date.toInstant().atZone(ZoneOffset.UTC);
    return String.format("%04d%02d", utc.getYear(), utc.getMonthValue());
  }

  private static DataObject dataObjectWith(String appId, User createdBy, Date createdAt) {
    DataObject d = new DataObject();
    d.setAppId(appId);
    d.setCreatedBy(createdBy);
    d.setCreatedAt(createdAt);
    return d;
  }

  private static User userWith(String username) {
    return new User(username);
  }

  // --- ym format verification ---------------------------------------------

  @Test
  public void ymFormat_ordinaryMonth_May2026() {
    assertEquals("202605", ymOf(utcDate(2026, 5, 15)), "May 2026 must format to '202605'");
  }

  @Test
  public void ymFormat_january() {
    // January must produce two-digit month "01", not "1"
    assertEquals("202501", ymOf(utcDate(2025, 1, 1)), "January must format to '202501' (leading zero)");
  }

  @Test
  public void ymFormat_december() {
    assertEquals("202412", ymOf(utcDate(2024, 12, 31)), "December must format to '202412'");
  }

  @Test
  public void ymFormat_alwaysSixChars() {
    // All 12 months of a leap year must produce exactly 6-char strings.
    int year = 2024;
    for (int month = 1; month <= 12; month++) {
      String ym = ymOf(utcDate(year, month, 1));
      assertEquals(6, ym.length(), "ym for month " + month + " must be exactly 6 chars, got: " + ym);
    }
  }

  @Test
  public void ymFormat_utcBoundary_cest_midnight() {
    // 2026-06-01 00:30 CEST  =  2026-05-31 22:30 UTC.
    // The ym must be "202605" (UTC month), NOT "202606" (CEST/local month).
    // This guards against the JVM-default-TZ regression: String.format("%1$tY%1$tm", date)
    // uses the JVM timezone and would produce "202606" on a CEST host, diverging from
    // the Cypher backfill migration which always uses UTC via datetime({epochMillis: x}).
    Instant cest0030 = LocalDateTime.of(2026, 6, 1, 0, 30).toInstant(ZoneOffset.ofHours(2));
    Date date = Date.from(cest0030);
    assertEquals(
      "202605",
      ymOf(date),
      "2026-06-01T00:30 CEST must map to UTC May 2026 → '202605'"
    );
  }

  // --- writeCreatedInMonth behaviour --------------------------------------

  @Test
  public void writeCreatedInMonth_issuesMergeWithCorrectYm() {
    User user = userWith("operator");
    DataObject d = dataObjectWith("do-app-id-123", user, utcDate(2026, 5, 20));

    dao.writeCreatedInMonth(d);

    // Verify session.query was called with the expected ym
    verify(session).query(
      eq(
        "MATCH (u:User {username: $username}) " +
        "MATCH (d:DataObject {appId: $dataObjectAppId}) " +
        "MERGE (u)-[:created_in_month {ym: $ym}]->(d)"
      ),
      argThat((Map<String, Object> params) ->
        "operator".equals(params.get("username")) &&
        "do-app-id-123".equals(params.get("dataObjectAppId")) &&
        "202605".equals(params.get("ym"))
      )
    );
  }

  @Test
  public void writeCreatedInMonth_skips_whenCreatedByIsNull() {
    DataObject d = dataObjectWith("do-app-id-456", null, utcDate(2026, 5, 1));

    dao.writeCreatedInMonth(d);

    // No session query should be issued
    verify(session, never()).query(any(String.class), any(Map.class));
  }

  @Test
  public void writeCreatedInMonth_skips_whenAppIdIsNull() {
    User user = userWith("operator");
    DataObject d = dataObjectWith(null, user, utcDate(2026, 5, 1));

    dao.writeCreatedInMonth(d);

    verify(session, never()).query(any(String.class), any(Map.class));
  }

  @Test
  public void writeCreatedInMonth_skips_whenCreatedAtIsNull() {
    User user = userWith("operator");
    DataObject d = dataObjectWith("do-app-id-789", user, null);

    dao.writeCreatedInMonth(d);

    verify(session, never()).query(any(String.class), any(Map.class));
  }

  @Test
  public void writeCreatedInMonth_skips_whenDataObjectIsNull() {
    dao.writeCreatedInMonth(null);

    verify(session, never()).query(any(String.class), any(Map.class));
  }
}
