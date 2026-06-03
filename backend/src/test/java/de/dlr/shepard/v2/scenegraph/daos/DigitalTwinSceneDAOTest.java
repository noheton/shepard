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

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.neo4j.ogm.session.Session;

/**
 * DT1-DAO-FRESH-SESSION — unit tests for {@link DigitalTwinSceneDAO}.
 *
 * <p>{@code createOrUpdate_*} tests use {@code mockStatic(NeoConnector.class)} to
 * verify the fresh-session override (CHOKE-03 fix) rather than the inherited
 * {@code this.session} field. The {@code findAll} / {@code findByNeo4jId} tests
 * still use {@code @InjectMocks} + {@code @Mock Session} because those methods
 * delegate to the base-class {@code session} field unchanged.
 */
public class DigitalTwinSceneDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private DigitalTwinSceneDAO dao = new DigitalTwinSceneDAO();

  // ---- helpers ---------------------------------------------------------------

  /**
   * Reflectively null out the cached {@code session} field that
   * {@code GenericDAO}'s constructor eagerly assigns. Simulates the CHOKE-03
   * startup race where the bean is built before the OGM {@code SessionFactory}
   * is ready.
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

  // ---- entity-type -----------------------------------------------------------

  @Test
  public void getEntityType_returnsDigitalTwinScene() {
    assertSame(DigitalTwinScene.class, dao.getEntityType());
  }

  // ---- createOrUpdate — fresh-session path (DT1-DAO-FRESH-SESSION) -----------

  @Test
  public void createOrUpdate_mintsAppId_whenNull() {
    DigitalTwinSceneDAO freshDao = new DigitalTwinSceneDAO();
    nullOutCachedSession(freshDao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var scene = new DigitalTwinScene();
    scene.setName("test-scene");
    assertNull(scene.getAppId(), "precondition: appId starts null");

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      var saved = freshDao.createOrUpdate(scene);

      assertNotNull(saved.getAppId(), "appId should be populated after save");
      assertEquals(36, saved.getAppId().length(), "appId should be canonical 36-char UUID");
      var parsed = UUID.fromString(saved.getAppId());
      assertEquals(7, parsed.version(), "L2a requires UUID v7");
      verify(live, times(1)).save(eq(scene), anyInt());
    }
  }

  @Test
  public void createOrUpdate_preservesAppId_whenAlreadySet() {
    DigitalTwinSceneDAO freshDao = new DigitalTwinSceneDAO();
    nullOutCachedSession(freshDao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var scene = new DigitalTwinScene();
    var existing = "0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506";
    scene.setAppId(existing);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      var saved = freshDao.createOrUpdate(scene);

      assertEquals(existing, saved.getAppId(), "existing appId must be preserved");
      verify(live, times(1)).save(eq(scene), anyInt());
    }
  }

  @Test
  public void createOrUpdate_returnsNull_whenEntityIsNull() {
    DigitalTwinSceneDAO freshDao = new DigitalTwinSceneDAO();
    nullOutCachedSession(freshDao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      assertNull(freshDao.createOrUpdate(null));
    }
  }

  @Test
  public void createOrUpdate_withNullSessionFactory_throwsIllegalState() {
    DigitalTwinSceneDAO freshDao = new DigitalTwinSceneDAO();
    nullOutCachedSession(freshDao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      assertThrows(IllegalStateException.class, () -> freshDao.createOrUpdate(new DigitalTwinScene()));
    }
  }

  // ---- finder methods (still use inherited this.session) ---------------------

  @Test
  public void findAll_delegatesToSession() {
    var a = new DigitalTwinScene(1L);
    var b = new DigitalTwinScene(2L);
    when(session.loadAll(DigitalTwinScene.class, 1)).thenReturn(List.of(a, b));

    var actual = dao.findAll();

    assertTrue(actual.containsAll(List.of(a, b)));
    assertEquals(2, actual.size());
  }

  @Test
  public void findByNeo4jId_delegatesToSession() {
    var scene = new DigitalTwinScene(42L);
    when(session.load(eq(DigitalTwinScene.class), eq(42L), eq(1))).thenReturn(scene);

    var actual = dao.findByNeo4jId(42L);

    assertSame(scene, actual);
  }
}
