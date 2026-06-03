package de.dlr.shepard.v2.scenegraph.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.neo4j.ogm.session.Session;

/**
 * DT1-DAO-FRESH-SESSION — unit tests for {@link DigitalTwinSceneDAO}.
 *
 * <p>Verifies that {@code createOrUpdate} and {@code findAll} use a live
 * OGM session fetched per call via {@code NeoConnector.getInstance()
 * .getNeo4jSession()} rather than the cached {@code session} field
 * inherited from {@code GenericDAO} (CHOKE-03 fix). Mirrors the
 * {@code SqlTimeseriesConfigDAOTest} / {@code InstanceRorConfigDAOTest}
 * test shape.
 */
public class DigitalTwinSceneDAOTest {

  /**
   * Reflectively null out the cached {@code session} field inherited
   * from {@code GenericDAO} to simulate the startup race condition where
   * the bean is constructed before the OGM {@code SessionFactory} is ready.
   */
  private static void nullOutCachedSession(DigitalTwinSceneDAO dao) {
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
  public void getEntityType_returnsDigitalTwinScene() {
    assertSame(DigitalTwinScene.class, new DigitalTwinSceneDAO().getEntityType());
  }

  @Test
  public void createOrUpdate_mintsAppId_whenNull() {
    DigitalTwinSceneDAO dao = new DigitalTwinSceneDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var scene = new DigitalTwinScene();
    scene.setName("test-scene");
    assertNull(scene.getAppId(), "precondition: appId starts null");

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var saved = dao.createOrUpdate(scene);

      assertNotNull(saved.getAppId(), "appId should be populated after save");
      assertEquals(36, saved.getAppId().length(), "appId should be canonical 36-char UUID");
      var parsed = UUID.fromString(saved.getAppId());
      assertEquals(7, parsed.version(), "L2a requires UUID v7");
    }
    verify(live, times(1)).save(scene, 1);
  }

  @Test
  public void createOrUpdate_preservesAppId_whenAlreadySet() {
    DigitalTwinSceneDAO dao = new DigitalTwinSceneDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var scene = new DigitalTwinScene();
    var existing = "0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506";
    scene.setAppId(existing);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var saved = dao.createOrUpdate(scene);
      assertEquals(existing, saved.getAppId(), "existing appId must be preserved");
    }
    verify(live, times(1)).save(scene, 1);
  }

  @Test
  public void createOrUpdate_withNullSessionFactory_throwsIllegalState() {
    DigitalTwinSceneDAO dao = new DigitalTwinSceneDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertThrows(IllegalStateException.class, () -> dao.createOrUpdate(new DigitalTwinScene()));
    }
  }

  @Test
  public void findAll_delegatesToLiveSession() {
    DigitalTwinSceneDAO dao = new DigitalTwinSceneDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var a = new DigitalTwinScene(1L);
    var b = new DigitalTwinScene(2L);
    when(live.loadAll(DigitalTwinScene.class, 1)).thenReturn(List.of(a, b));

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var actual = dao.findAll();
      assertTrue(actual.containsAll(List.of(a, b)));
      assertEquals(2, actual.size());
    }
    verify(live, times(1)).loadAll(eq(DigitalTwinScene.class), anyInt());
  }

  @Test
  public void findAll_withNullSessionFactory_returnsEmptyList() {
    DigitalTwinSceneDAO dao = new DigitalTwinSceneDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var actual = dao.findAll();
      assertTrue(actual.isEmpty());
    }
  }
}
