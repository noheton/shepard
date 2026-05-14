package de.dlr.shepard.versioning;

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
import de.dlr.shepard.auth.permission.model.Permissions;
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

/**
 * ENT1a DAO tests — Mockito on the Neo4j session, mirroring the
 * shape of {@code PublicationDAOTest} from KIP1a.
 */
class EntityVersionDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private EntityVersionDAO dao = new EntityVersionDAO();

  private EntityVersion version(String label, int ordinal) {
    EntityVersion v = new EntityVersion();
    v.setAppId("v-app-" + label);
    v.setVersionLabel(label);
    v.setVersionOrdinal(ordinal);
    v.setCreatedAt(1_747_000_000_000L);
    v.setCreatedBy("alice");
    v.setParentEntityKind("collection");
    v.setParentEntityAppId("01HF-A");
    return v;
  }

  @Test
  void entityTypeIsEntityVersion() {
    assertEquals(EntityVersion.class, dao.getEntityType());
  }

  // ─── findByParentAndLabel ─────────────────────────────────────────────

  @Test
  void findByParentAndLabelHits() {
    EntityVersion v = version("v1", 1);
    when(session.query(eq(EntityVersion.class), any(String.class), anyMap())).thenReturn(List.of(v));
    var out = dao.findByParentAndLabel("01HF-A", "v1");
    assertTrue(out.isPresent());
    assertSame(v, out.get());
  }

  @Test
  void findByParentAndLabelMisses() {
    when(session.query(eq(EntityVersion.class), any(String.class), anyMap())).thenReturn(Collections.emptyList());
    assertFalse(dao.findByParentAndLabel("01HF-A", "nope").isPresent());
  }

  @Test
  void findByParentAndLabelRejectsBlankInputs() {
    assertFalse(dao.findByParentAndLabel(null, "v1").isPresent());
    assertFalse(dao.findByParentAndLabel("", "v1").isPresent());
    assertFalse(dao.findByParentAndLabel("01HF-A", null).isPresent());
    assertFalse(dao.findByParentAndLabel("01HF-A", "").isPresent());
    verify(session, never()).query(eq(EntityVersion.class), any(String.class), anyMap());
  }

  // ─── findAllByParent ──────────────────────────────────────────────────

  @Test
  void findAllByParentReturnsOrderedList() {
    EntityVersion a = version("v3", 3);
    EntityVersion b = version("v2", 2);
    EntityVersion c = version("v1", 1);
    when(session.query(eq(EntityVersion.class), any(String.class), anyMap())).thenReturn(List.of(a, b, c));
    var out = dao.findAllByParent("01HF-A");
    assertEquals(3, out.size());
    assertSame(a, out.get(0));
    assertSame(b, out.get(1));
    assertSame(c, out.get(2));
  }

  @Test
  void findAllByParentRejectsBlank() {
    assertTrue(dao.findAllByParent(null).isEmpty());
    assertTrue(dao.findAllByParent("").isEmpty());
    verify(session, never()).query(eq(EntityVersion.class), any(String.class), anyMap());
  }

  // ─── findLatestByParent ───────────────────────────────────────────────

  @Test
  void findLatestByParentReturnsTopRow() {
    EntityVersion latest = version("v5", 5);
    when(session.query(eq(EntityVersion.class), any(String.class), anyMap())).thenReturn(List.of(latest));
    var out = dao.findLatestByParent("01HF-A");
    assertTrue(out.isPresent());
    assertSame(latest, out.get());
  }

  @Test
  void findLatestByParentEmpty() {
    when(session.query(eq(EntityVersion.class), any(String.class), anyMap())).thenReturn(Collections.emptyList());
    assertFalse(dao.findLatestByParent("01HF-A").isPresent());
  }

  @Test
  void findLatestByParentRejectsBlank() {
    assertFalse(dao.findLatestByParent(null).isPresent());
    assertFalse(dao.findLatestByParent("").isPresent());
    verify(session, never()).query(eq(EntityVersion.class), any(String.class), anyMap());
  }

  // ─── findMaxOrdinalByParent ───────────────────────────────────────────

  @Test
  void findMaxOrdinalByParentRejectsBlank() {
    assertEquals(0, dao.findMaxOrdinalByParent(null));
    assertEquals(0, dao.findMaxOrdinalByParent(""));
    verify(session, never()).query(any(String.class), anyMap());
  }

  @Test
  void findMaxOrdinalByParentReturnsZeroWhenNoVersions() {
    Result r = mock(Result.class);
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(Map.<String, Object>of("maxOrdinal", 0L)).iterator();
    when(r.iterator()).thenReturn(iter);
    when(session.query(any(String.class), anyMap())).thenReturn(r);
    assertEquals(0, dao.findMaxOrdinalByParent("01HF-FRESH"));
  }

  @Test
  void findMaxOrdinalByParentReturnsHighestOrdinal() {
    Result r = mock(Result.class);
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(Map.<String, Object>of("maxOrdinal", 7L)).iterator();
    when(r.iterator()).thenReturn(iter);
    when(session.query(any(String.class), anyMap())).thenReturn(r);
    assertEquals(7, dao.findMaxOrdinalByParent("01HF-A"));
  }

  @Test
  void findMaxOrdinalByParentHandlesEmptyResultIterator() {
    Result r = mock(Result.class);
    when(r.iterator()).thenReturn(Collections.<Map<String, Object>>emptyList().iterator());
    when(session.query(any(String.class), anyMap())).thenReturn(r);
    assertEquals(0, dao.findMaxOrdinalByParent("01HF-A"));
  }

  @Test
  void findMaxOrdinalByParentHandlesNonNumericValue() {
    Result r = mock(Result.class);
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(Map.<String, Object>of("maxOrdinal", "x")).iterator();
    when(r.iterator()).thenReturn(iter);
    when(session.query(any(String.class), anyMap())).thenReturn(r);
    assertEquals(0, dao.findMaxOrdinalByParent("01HF-A"));
  }

  @Test
  void findMaxOrdinalByParentAcceptsIntegerAndLong() {
    Result r = mock(Result.class);
    when(r.iterator()).thenReturn(
      List.<Map<String, Object>>of(Map.<String, Object>of("maxOrdinal", Integer.valueOf(4))).iterator()
    );
    when(session.query(any(String.class), anyMap())).thenReturn(r);
    assertEquals(4, dao.findMaxOrdinalByParent("01HF-A"));
  }

  // ─── existsLabelForParent ─────────────────────────────────────────────

  @Test
  void existsLabelForParentTrueWhenCountPositive() {
    Result r = mock(Result.class);
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(Map.<String, Object>of("c", 1L)).iterator();
    when(r.iterator()).thenReturn(iter);
    when(session.query(any(String.class), anyMap())).thenReturn(r);
    assertTrue(dao.existsLabelForParent("01HF-A", "v1"));
  }

  @Test
  void existsLabelForParentFalseWhenCountZero() {
    Result r = mock(Result.class);
    Iterator<Map<String, Object>> iter = List.<Map<String, Object>>of(Map.<String, Object>of("c", 0L)).iterator();
    when(r.iterator()).thenReturn(iter);
    when(session.query(any(String.class), anyMap())).thenReturn(r);
    assertFalse(dao.existsLabelForParent("01HF-A", "v99"));
  }

  @Test
  void existsLabelForParentRejectsBlank() {
    assertFalse(dao.existsLabelForParent(null, "v1"));
    assertFalse(dao.existsLabelForParent("01HF-A", null));
    assertFalse(dao.existsLabelForParent("", "v1"));
    assertFalse(dao.existsLabelForParent("01HF-A", ""));
  }

  // ─── save / attachToParent / delete ───────────────────────────────────

  @Test
  void saveRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> dao.save(null));
  }

  @Test
  void saveMintsAppIdViaCreateOrUpdate() {
    EntityVersion v = new EntityVersion();
    v.setVersionLabel("v2");
    v.setVersionOrdinal(2);
    v.setParentEntityKind("collection");
    v.setParentEntityAppId("01HF-A");
    EntityVersion saved = dao.save(v);
    assertTrue(saved.getAppId() != null && !saved.getAppId().isBlank());
    verify(session, times(1)).save(eq(v), eq(1));
  }

  @Test
  void attachToParentRejectsBadInputs() {
    assertThrows(IllegalArgumentException.class, () -> dao.attachToParent(null, "01HF-A"));
    EntityVersion noAppId = new EntityVersion();
    assertThrows(IllegalArgumentException.class, () -> dao.attachToParent(noAppId, "01HF-A"));
    EntityVersion v = version("v1", 1);
    assertThrows(IllegalArgumentException.class, () -> dao.attachToParent(v, null));
    assertThrows(IllegalArgumentException.class, () -> dao.attachToParent(v, ""));
  }

  @Test
  void attachToParentRunsMergeQuery() {
    Result r = mock(Result.class);
    QueryStatistics stats = mock(QueryStatistics.class);
    when(stats.containsUpdates()).thenReturn(true);
    when(r.queryStatistics()).thenReturn(stats);
    when(session.query(any(String.class), anyMap())).thenReturn(r);
    EntityVersion v = version("v1", 1);
    dao.attachToParent(v, "01HF-A");
    verify(session, times(1)).query(any(String.class), eq(Map.of("parentAppId", "01HF-A", "versionAppId", v.getAppId())));
  }

  @Test
  void deleteRunsDetachQuery() {
    Result r = mock(Result.class);
    QueryStatistics stats = mock(QueryStatistics.class);
    when(stats.containsUpdates()).thenReturn(true);
    when(r.queryStatistics()).thenReturn(stats);
    when(session.query(any(String.class), anyMap())).thenReturn(r);
    EntityVersion v = version("v2", 2);
    dao.delete(v);
    verify(session, times(1)).query(any(String.class), eq(Map.of("versionAppId", v.getAppId())));
  }

  @Test
  void deleteIsNoopOnNullOrNoAppId() {
    dao.delete(null);
    dao.delete(new EntityVersion());
    verify(session, never()).query(any(String.class), anyMap());
  }

  @Test
  void setPermissionsRejectsUnsavedInputs() {
    Permissions p = new Permissions();
    p.setAppId("perm-1");
    EntityVersion noAppId = new EntityVersion();
    assertThrows(IllegalArgumentException.class, () -> dao.setPermissions(noAppId, p));
    EntityVersion v = version("v1", 1);
    Permissions noPermAppId = new Permissions();
    assertThrows(IllegalArgumentException.class, () -> dao.setPermissions(v, noPermAppId));
    assertThrows(IllegalArgumentException.class, () -> dao.setPermissions(v, null));
    assertThrows(IllegalArgumentException.class, () -> dao.setPermissions(null, p));
  }

  @Test
  void setPermissionsRunsMergeQuery() {
    Result r = mock(Result.class);
    QueryStatistics stats = mock(QueryStatistics.class);
    when(stats.containsUpdates()).thenReturn(true);
    when(r.queryStatistics()).thenReturn(stats);
    when(session.query(any(String.class), anyMap())).thenReturn(r);
    EntityVersion v = version("v1", 1);
    Permissions p = new Permissions();
    p.setAppId("perm-1");
    dao.setPermissions(v, p);
    verify(session, times(1)).query(any(String.class), eq(Map.of("versionAppId", v.getAppId(), "permsAppId", "perm-1")));
  }

  @Test
  void hasEntityVersionConstantValue() {
    assertEquals("HAS_ENTITY_VERSION", EntityVersionDAO.HAS_ENTITY_VERSION);
  }
}
