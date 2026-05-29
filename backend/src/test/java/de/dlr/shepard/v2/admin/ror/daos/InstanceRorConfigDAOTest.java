package de.dlr.shepard.v2.admin.ror.daos;

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
import de.dlr.shepard.v2.admin.ror.entities.InstanceRorConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.neo4j.ogm.session.Session;

/**
 * ROR1 regression — TS-INGEST-222GB-CHOKE-03.
 *
 * <p>Verifies that {@link InstanceRorConfigDAO#findSingleton()} and
 * {@link InstanceRorConfigDAO#createOrUpdate(InstanceRorConfig)} no
 * longer rely on the cached {@code this.session} field inherited from
 * {@code GenericDAO}. Mirrors the {@code JupyterConfigDAO} shape
 * established by commit {@code 58b5b10fb}.
 */
class InstanceRorConfigDAOTest {

  /**
   * Reflectively clear the cached {@code session} field that
   * {@code GenericDAO}'s constructor would have eagerly assigned. This
   * simulates the startup race where the bean is constructed before
   * the OGM {@code SessionFactory} is ready, leaving the cached
   * reference {@code null} forever.
   */
  private static void nullOutCachedSession(InstanceRorConfigDAO dao) {
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
    InstanceRorConfigDAO dao = new InstanceRorConfigDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    InstanceRorConfig row = new InstanceRorConfig();
    row.setAppId("seeded");
    when(live.loadAll(eq(InstanceRorConfig.class), anyInt())).thenReturn(List.of(row));

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      InstanceRorConfig out = dao.findSingleton();
      assertNotNull(out);
      assertEquals("seeded", out.getAppId());
    }
    verify(live, times(1)).loadAll(eq(InstanceRorConfig.class), anyInt());
  }

  @Test
  void findSingleton_withNullSessionFactory_returnsNullInsteadOfNpe() {
    InstanceRorConfigDAO dao = new InstanceRorConfigDAO();
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
    InstanceRorConfigDAO dao = new InstanceRorConfigDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    InstanceRorConfig fresh = new InstanceRorConfig();

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      InstanceRorConfig saved = dao.createOrUpdate(fresh);
      assertNotNull(saved);
      assertNotNull(saved.getAppId());
    }
    verify(live, times(1)).save(eq(fresh), anyInt());
  }

  @Test
  void createOrUpdate_withNullSessionFactory_throwsIllegalStateNotNpe() {
    InstanceRorConfigDAO dao = new InstanceRorConfigDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertThrows(IllegalStateException.class, () -> dao.createOrUpdate(new InstanceRorConfig()));
    }
  }
}
