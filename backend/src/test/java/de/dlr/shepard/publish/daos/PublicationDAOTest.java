package de.dlr.shepard.publish.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.publish.entities.Publication;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.QueryStatistics;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

class PublicationDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private PublicationDAO dao = new PublicationDAO();

  private Publication publication() {
    Publication p = new Publication();
    p.setAppId("pub-1");
    p.setPid("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v1");
    p.setMintedAt(1_747_000_000_000L);
    p.setMinterId("local");
    p.setEntityKind("data-objects");
    p.setEntityAppId("01HF-A");
    p.setVersionNumber(1);
    return p;
  }

  @Test
  void entityTypeIsPublication() {
    assertEquals(Publication.class, dao.getEntityType());
  }

  @Test
  void findByPidHits() {
    Publication p = publication();
    when(session.query(eq(Publication.class), any(String.class), anyMap())).thenReturn(List.of(p));
    var out = dao.findByPid(p.getPid());
    assertTrue(out.isPresent());
    assertSame(p, out.get());
  }

  @Test
  void findByPidMisses() {
    when(session.query(eq(Publication.class), any(String.class), anyMap())).thenReturn(Collections.emptyList());
    assertFalse(dao.findByPid("nope").isPresent());
  }

  @Test
  void findByPidRejectsBlankInputEarly() {
    // null + blank short-circuit before the session is hit.
    assertFalse(dao.findByPid(null).isPresent());
    assertFalse(dao.findByPid("").isPresent());
    assertFalse(dao.findByPid("   ").isPresent());
    verify(session, never()).query(eq(Publication.class), any(String.class), anyMap());
  }

  @Test
  void findByEntityAppIdReturnsList() {
    Publication a = publication();
    Publication b = publication();
    b.setAppId("pub-2");
    b.setPid("shepard:dlr.de/shepard-prod:data-objects:01HF-A:v2");
    b.setVersionNumber(2);
    when(session.query(eq(Publication.class), any(String.class), anyMap())).thenReturn(List.of(a, b));
    var out = dao.findByEntityAppId("01HF-A");
    assertEquals(2, out.size());
    assertSame(a, out.get(0));
    assertSame(b, out.get(1));
  }

  @Test
  void findByEntityAppIdRejectsBlank() {
    assertTrue(dao.findByEntityAppId(null).isEmpty());
    assertTrue(dao.findByEntityAppId("").isEmpty());
    verify(session, never()).query(eq(Publication.class), any(String.class), anyMap());
  }

  @Test
  void attachToEntityRejectsNullPublication() {
    assertThrows(IllegalArgumentException.class, () -> dao.attachToEntity(null, "01HF-A"));
  }

  @Test
  void attachToEntityRejectsBlankEntityAppId() {
    assertThrows(IllegalArgumentException.class, () -> dao.attachToEntity(publication(), ""));
    assertThrows(IllegalArgumentException.class, () -> dao.attachToEntity(publication(), null));
  }

  @Test
  void attachToEntityPersistsAndAttachesEdge() {
    Publication p = new Publication();
    p.setPid("pid-x");
    p.setMintedAt(1L);
    p.setMinterId("local");
    p.setEntityKind("data-objects");
    p.setVersionNumber(1);
    // Stub the MERGE-edge runQuery: GenericDAO#runQuery returns a
    // boolean derived from queryStatistics.containsUpdates().
    Result mergeResult = mock(Result.class);
    QueryStatistics stats = mock(QueryStatistics.class);
    when(stats.containsUpdates()).thenReturn(true);
    when(mergeResult.queryStatistics()).thenReturn(stats);
    when(session.query(any(String.class), anyMap())).thenReturn(mergeResult);

    Publication saved = dao.attachToEntity(p, "01HF-A");

    assertEquals("01HF-A", saved.getEntityAppId());
    // GenericDAO#createOrUpdate mints an appId because Publication implements HasAppId.
    assertTrue(saved.getAppId() != null && !saved.getAppId().isBlank());
    // Session.save must have happened (createOrUpdate path).
    verify(session, times(1)).save(eq(p), eq(1));
    // The MERGE edge query is invoked with the right params.
    verify(session, times(1)).query(any(String.class), eq(Map.of("entityAppId", "01HF-A", "pubAppId", saved.getAppId())));
  }

  @Test
  void hasPublicationConstantValue() {
    assertEquals("HAS_PUBLICATION", PublicationDAO.HAS_PUBLICATION);
  }

  // ---------- KIP1h: findLatestVersionNumber ----------

  @Test
  void findLatestVersionNumberRejectsBlank() {
    // Empty / null / blank short-circuits to 0 (no Cypher round-trip).
    assertEquals(0, dao.findLatestVersionNumber(null));
    assertEquals(0, dao.findLatestVersionNumber(""));
    assertEquals(0, dao.findLatestVersionNumber("   "));
    verify(session, never()).query(any(String.class), anyMap());
  }

  @Test
  void findLatestVersionNumberReturnsZeroWhenNoPublications() {
    // When the entity has no :Publication rows, coalesce(max(...), 0)
    // returns 0 in the Cypher result.
    Result r = mock(Result.class);
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(Map.<String, Object>of("maxVersion", 0L)).iterator();
    when(r.iterator()).thenReturn(iter);
    when(session.query(any(String.class), anyMap())).thenReturn(r);

    assertEquals(0, dao.findLatestVersionNumber("01HF-FRESH"));
  }

  @Test
  void findLatestVersionNumberReturnsHighestVersion() {
    // Entity already has v1, v2, v3 — next mint is v4.
    Result r = mock(Result.class);
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(Map.<String, Object>of("maxVersion", 3L)).iterator();
    when(r.iterator()).thenReturn(iter);
    when(session.query(any(String.class), anyMap())).thenReturn(r);

    assertEquals(3, dao.findLatestVersionNumber("01HF-A"));
  }

  @Test
  void findLatestVersionNumberHandlesEmptyResultIterator() {
    // Defensive — if the query returned no rows at all (impossible in
    // practice with coalesce, but pin the path) the DAO returns 0.
    Result r = mock(Result.class);
    when(r.iterator()).thenReturn(Collections.<Map<String, Object>>emptyList().iterator());
    when(session.query(any(String.class), anyMap())).thenReturn(r);

    assertEquals(0, dao.findLatestVersionNumber("01HF-A"));
  }

  @Test
  void findLatestVersionNumberHandlesNonNumericValue() {
    // Defensive — a malformed row (string, null) returns 0 rather
    // than throwing a ClassCastException.
    Result r = mock(Result.class);
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(
      Map.<String, Object>of("maxVersion", "not-a-number")
    ).iterator();
    when(r.iterator()).thenReturn(iter);
    when(session.query(any(String.class), anyMap())).thenReturn(r);

    assertEquals(0, dao.findLatestVersionNumber("01HF-A"));
  }

  @Test
  void findLatestVersionNumberAcceptsIntegerAndLongResults() {
    // Neo4j OGM may surface Integer or Long depending on the driver
    // build; both intValue() cleanly to int.
    Result r1 = mock(Result.class);
    when(r1.iterator()).thenReturn(
      List.<Map<String, Object>>of(Map.<String, Object>of("maxVersion", Integer.valueOf(7))).iterator()
    );
    when(session.query(any(String.class), anyMap())).thenReturn(r1);
    assertEquals(7, dao.findLatestVersionNumber("01HF-INT"));
  }

  // ---------- KIP1f: retireMostRecent ----------

  @Test
  void retireMostRecentReturnsFalseForBlankEntityAppId() {
    // Blank / null short-circuits before session is hit.
    assertFalse(dao.retireMostRecent(null));
    assertFalse(dao.retireMostRecent(""));
    assertFalse(dao.retireMostRecent("   "));
    verify(session, never()).query(any(String.class), anyMap());
  }

  @Test
  void retireMostRecentReturnsTrueWhenPublicationExists() {
    // Query returns count = 1 → row found, SET executed → true.
    Result r = mock(Result.class);
    when(r.iterator()).thenReturn(
      List.<Map<String, Object>>of(Map.<String, Object>of("n", 1L)).iterator()
    );
    when(session.query(any(String.class), anyMap())).thenReturn(r);

    assertTrue(dao.retireMostRecent("01HF-A"));
  }

  @Test
  void retireMostRecentReturnsFalseWhenNoPublicationExists() {
    // Query returns count = 0 → no row matched → false (REST maps to 404).
    Result r = mock(Result.class);
    when(r.iterator()).thenReturn(
      List.<Map<String, Object>>of(Map.<String, Object>of("n", 0L)).iterator()
    );
    when(session.query(any(String.class), anyMap())).thenReturn(r);

    assertFalse(dao.retireMostRecent("01HF-NO-PUB"));
  }

  @Test
  void retireMostRecentReturnsFalseOnEmptyQueryResult() {
    // Defensive: empty iterator (impossible in practice with RETURN count(p),
    // but pin the defensive path).
    Result r = mock(Result.class);
    when(r.iterator()).thenReturn(Collections.<Map<String, Object>>emptyList().iterator());
    when(session.query(any(String.class), anyMap())).thenReturn(r);

    assertFalse(dao.retireMostRecent("01HF-A"));
  }

  @Test
  void retireMostRecentIdempotent() {
    // Calling retire twice on an already-retired row returns true both
    // times — the Cypher SET is idempotent (same value → same effect).
    Result r1 = mock(Result.class);
    when(r1.iterator()).thenReturn(
      List.<Map<String, Object>>of(Map.<String, Object>of("n", 1L)).iterator()
    );
    Result r2 = mock(Result.class);
    when(r2.iterator()).thenReturn(
      List.<Map<String, Object>>of(Map.<String, Object>of("n", 1L)).iterator()
    );
    when(session.query(any(String.class), anyMap())).thenReturn(r1).thenReturn(r2);

    assertTrue(dao.retireMostRecent("01HF-A"));
    assertTrue(dao.retireMostRecent("01HF-A"));
    verify(session, times(2)).query(any(String.class), anyMap());
  }
}
