package de.dlr.shepard.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.model.QueryStatistics;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * PM1e — unit tests for {@link PluginRuntimeOverrideDAO}. Same
 * Mockito-on-Session idiom as {@code PublicationDAOTest} — the DAO
 * is exercised against a mocked {@link Session} so the test stays
 * fast and free of Neo4j.
 */
class PluginRuntimeOverrideDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private PluginRuntimeOverrideDAO dao = new PluginRuntimeOverrideDAO();

  private PluginRuntimeOverride row(String pluginId, boolean enabled) {
    PluginRuntimeOverride r = new PluginRuntimeOverride();
    r.setPluginId(pluginId);
    r.setEnabled(enabled);
    r.setUpdatedBy("test-actor");
    return r;
  }

  @Test
  void entityTypeIsPluginRuntimeOverride() {
    assertEquals(PluginRuntimeOverride.class, dao.getEntityType());
  }

  @Test
  void findByPluginIdHitsViaCypherIndex() {
    PluginRuntimeOverride r = row("unhide", false);
    when(session.query(eq(PluginRuntimeOverride.class), any(String.class), anyMap())).thenReturn(List.of(r));
    var out = dao.findByPluginId("unhide");
    assertTrue(out.isPresent());
    assertSame(r, out.get());
  }

  @Test
  void findByPluginIdMissReturnsEmpty() {
    when(session.query(eq(PluginRuntimeOverride.class), any(String.class), anyMap())).thenReturn(Collections.emptyList());
    assertFalse(dao.findByPluginId("ghost").isPresent());
  }

  @Test
  void findByPluginIdShortCircuitsOnBlank() {
    // null + blank short-circuit before the session is touched.
    assertFalse(dao.findByPluginId(null).isPresent());
    assertFalse(dao.findByPluginId("").isPresent());
    assertFalse(dao.findByPluginId("   ").isPresent());
    verify(session, never()).query(eq(PluginRuntimeOverride.class), any(String.class), anyMap());
  }

  @Test
  void findAllOverridesReturnsAllRows() {
    PluginRuntimeOverride a = row("unhide", false);
    PluginRuntimeOverride b = row("kip", true);
    when(session.loadAll(eq(PluginRuntimeOverride.class), anyInt())).thenReturn(List.of(a, b));
    List<PluginRuntimeOverride> all = dao.findAllOverrides();
    assertEquals(2, all.size());
    assertTrue(all.contains(a));
    assertTrue(all.contains(b));
  }

  @Test
  void findAllOverridesEmptyReturnsEmptyList() {
    when(session.loadAll(eq(PluginRuntimeOverride.class), anyInt())).thenReturn(Collections.emptyList());
    assertTrue(dao.findAllOverrides().isEmpty());
  }

  @Test
  void findAllOverridesNullReturnsEmptyList() {
    when(session.loadAll(eq(PluginRuntimeOverride.class), anyInt())).thenReturn(null);
    assertTrue(dao.findAllOverrides().isEmpty());
  }

  @Test
  void savePersistsViaGenericDaoAndMintsAppId() {
    PluginRuntimeOverride r = row("unhide", false);
    // Sanity: before save, no appId yet.
    assertEquals(null, r.getAppId());
    PluginRuntimeOverride saved = dao.save(r);
    // GenericDAO#createOrUpdate mints the appId because HasAppId
    // is implemented.
    assertTrue(saved.getAppId() != null && !saved.getAppId().isBlank());
    verify(session).save(eq(r), eq(1));
  }

  @Test
  void saveRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> dao.save(null));
  }

  @Test
  void saveRejectsBlankPluginId() {
    PluginRuntimeOverride r = new PluginRuntimeOverride();
    r.setPluginId("");
    assertThrows(IllegalArgumentException.class, () -> dao.save(r));
    r.setPluginId(null);
    assertThrows(IllegalArgumentException.class, () -> dao.save(r));
    r.setPluginId("   ");
    assertThrows(IllegalArgumentException.class, () -> dao.save(r));
  }

  @Test
  void deleteByPluginIdRunsDetachDelete() {
    Result result = mock(Result.class);
    QueryStatistics stats = mock(QueryStatistics.class);
    when(stats.containsUpdates()).thenReturn(true);
    when(result.queryStatistics()).thenReturn(stats);
    when(session.query(any(String.class), anyMap())).thenReturn(result);

    boolean removed = dao.deleteByPluginId("unhide");
    assertTrue(removed);
    verify(session).query(any(String.class), eq(Map.of("pluginId", "unhide")));
  }

  @Test
  void deleteByPluginIdMissReturnsFalse() {
    Result result = mock(Result.class);
    QueryStatistics stats = mock(QueryStatistics.class);
    when(stats.containsUpdates()).thenReturn(false);
    when(result.queryStatistics()).thenReturn(stats);
    when(session.query(any(String.class), anyMap())).thenReturn(result);

    assertFalse(dao.deleteByPluginId("ghost"));
  }

  @Test
  void deleteByPluginIdShortCircuitsOnBlank() {
    assertFalse(dao.deleteByPluginId(null));
    assertFalse(dao.deleteByPluginId(""));
    assertFalse(dao.deleteByPluginId("   "));
    verify(session, never()).query(any(String.class), anyMap());
  }
}
