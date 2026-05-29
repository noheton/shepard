package de.dlr.shepard.context.semantic.daos;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.neo4j.ogm.session.Session;

/**
 * N1c2 regression — TS-INGEST-222GB-CHOKE-03.
 *
 * <p>Although {@link SemanticConfigDAO} is {@code @RequestScoped}, it is
 * also manually instantiated by
 * {@code OntologySeedService.productionConfigService()} at startup —
 * before the {@code SessionFactory} is ready. The cached
 * {@code this.session} field is therefore null on that code path, and
 * the unfixed {@link SemanticConfigDAO#findFirst()} /
 * {@link SemanticConfigDAO#createOrUpdate(SemanticConfig)} would NPE.
 *
 * <p>Mirrors the {@code JupyterConfigDAO} fix shape from commit
 * {@code 58b5b10fb}.
 */
class SemanticConfigDAOTest {

  /**
   * Reflectively clear the cached {@code session} field that
   * {@code GenericDAO}'s constructor would have eagerly assigned. This
   * simulates the startup race where the DAO is constructed before
   * the OGM {@code SessionFactory} is ready, leaving the cached
   * reference {@code null} forever.
   */
  private static void nullOutCachedSession(SemanticConfigDAO dao) {
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
  void findFirst_withNullCachedSession_doesNotNpeAndFallsThroughToLiveSession() {
    SemanticConfigDAO dao = new SemanticConfigDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    SemanticConfig row = new SemanticConfig();
    when(live.loadAll(eq(SemanticConfig.class), anyInt())).thenReturn(List.of(row));

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      SemanticConfig out = dao.findFirst();
      assertSame(row, out);
    }
    verify(live, times(1)).loadAll(eq(SemanticConfig.class), anyInt());
  }

  @Test
  void findFirst_withNullSessionFactory_returnsNullInsteadOfNpe() {
    SemanticConfigDAO dao = new SemanticConfigDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertNull(dao.findFirst());
    }
  }

  @Test
  void createOrUpdate_withNullCachedSession_persistsViaLiveSession() {
    SemanticConfigDAO dao = new SemanticConfigDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    SemanticConfig fresh = new SemanticConfig();

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      SemanticConfig saved = dao.createOrUpdate(fresh);
      assertNotNull(saved);
      assertNotNull(saved.getAppId());
    }
    verify(live, times(1)).save(eq(fresh), anyInt());
  }

  @Test
  void createOrUpdate_withNullSessionFactory_throwsIllegalStateNotNpe() {
    SemanticConfigDAO dao = new SemanticConfigDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertThrows(IllegalStateException.class, () -> dao.createOrUpdate(new SemanticConfig()));
    }
  }
}
