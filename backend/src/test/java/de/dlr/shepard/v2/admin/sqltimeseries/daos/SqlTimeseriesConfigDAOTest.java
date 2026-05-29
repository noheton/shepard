package de.dlr.shepard.v2.admin.sqltimeseries.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.v2.admin.sqltimeseries.entities.SqlTimeseriesConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.neo4j.ogm.session.Session;

/**
 * P10c regression — TS-INGEST-222GB-CHOKE-03.
 *
 * <p>Verifies that {@link SqlTimeseriesConfigDAO#findSingleton()} and
 * {@link SqlTimeseriesConfigDAO#createOrUpdate(SqlTimeseriesConfig)} no
 * longer rely on the cached {@code this.session} field inherited from
 * {@code GenericDAO}. That field is set at bean construction (which can
 * happen before {@code SessionFactory} is ready) and would stay
 * {@code null} forever, producing NPEs on every admin call. The fix —
 * fetch a live session per call via
 * {@code NeoConnector.getInstance().getNeo4jSession()} — mirrors the
 * {@code JupyterConfigDAO} shape established by commit {@code 58b5b10fb}.
 */
class SqlTimeseriesConfigDAOTest {

  /**
   * Reflectively clear the cached {@code session} field that
   * {@code GenericDAO}'s constructor would have eagerly assigned. This
   * simulates the startup race where the bean is constructed before
   * the OGM {@code SessionFactory} is ready, leaving the cached
   * reference {@code null} forever.
   */
  private static void nullOutCachedSession(SqlTimeseriesConfigDAO dao) {
    try {
      java.lang.reflect.Field f =
        de.dlr.shepard.common.neo4j.daos.GenericDAO.class.getDeclaredField("session");
      f.setAccessible(true);
      f.set(dao, null);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void findSingleton_withNullCachedSession_doesNotNpeAndFallsThroughToLiveSession() {
    // Simulate the startup race: cached this.session is null but
    // NeoConnector now has a working session.
    SqlTimeseriesConfigDAO dao = new SqlTimeseriesConfigDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    SqlTimeseriesConfig row = new SqlTimeseriesConfig();
    row.setAppId("seeded");
    when(live.loadAll(eq(SqlTimeseriesConfig.class), anyInt())).thenReturn(List.of(row));

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      SqlTimeseriesConfig out = dao.findSingleton();
      assertNotNull(out);
      assertEquals("seeded", out.getAppId());
    }
    verify(live, times(1)).loadAll(eq(SqlTimeseriesConfig.class), anyInt());
  }

  @Test
  void findSingleton_withNullSessionFactory_returnsNullInsteadOfNpe() {
    SqlTimeseriesConfigDAO dao = new SqlTimeseriesConfigDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertNull(dao.findSingleton());
    }
  }

  @Test
  void createOrUpdate_withNullCachedSession_persistsViaLiveSession() {
    SqlTimeseriesConfigDAO dao = new SqlTimeseriesConfigDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    SqlTimeseriesConfig fresh = new SqlTimeseriesConfig();

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      SqlTimeseriesConfig saved = dao.createOrUpdate(fresh);
      assertNotNull(saved);
      // appId was minted because the entity arrived without one
      assertNotNull(saved.getAppId());
    }
    verify(live, times(1)).save(eq(fresh), anyInt());
  }

  @Test
  void createOrUpdate_withNullSessionFactory_throwsIllegalStateNotNpe() {
    SqlTimeseriesConfigDAO dao = new SqlTimeseriesConfigDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertThrows(IllegalStateException.class, () -> dao.createOrUpdate(new SqlTimeseriesConfig()));
    }
  }
}
