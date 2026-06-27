package de.dlr.shepard.v2.scenegraph.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * DT1-PHASE-0 / DT1-DAO-FRESH-SESSION — unit tests for {@link DigitalTwinSceneDAO}.
 *
 * <p>Overridden methods ({@code createOrUpdate}, {@code findByAppId}) are tested
 * via {@code mockStatic(NeoConnector.class)} after clearing the cached
 * {@code this.session} field (CHOKE-03 / JupyterConfig pattern). Inherited
 * methods ({@code findAll}, {@code findByNeo4jId}) still use the {@code @Mock}
 * session injected by Mockito's {@code @InjectMocks}.
 */
public class DigitalTwinSceneDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private DigitalTwinSceneDAO dao = new DigitalTwinSceneDAO();

  /**
   * Simulates the CHOKE-03 startup race: the cached {@code this.session} that
   * {@code GenericDAO}'s constructor assigned is nulled out, forcing overridden
   * methods to fall back to {@code NeoConnector.getInstance().getNeo4jSession()}.
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
    assertSame(DigitalTwinScene.class, dao.getEntityType());
  }

  @Test
  public void createOrUpdate_mintsAppId_whenNull() {
    nullOutCachedSession(dao);
    var scene = new DigitalTwinScene();
    scene.setName("test-scene");
    assertNull(scene.getAppId(), "precondition: appId starts null");

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var saved = dao.createOrUpdate(scene);
      assertNotNull(saved.getAppId(), "appId should be populated after save");
      assertEquals(36, saved.getAppId().length(), "appId should be canonical 36-char UUID");
      var parsed = UUID.fromString(saved.getAppId());
      assertEquals(7, parsed.version(), "L2a requires UUID v7");
    }
    verify(live, times(1)).save(eq(scene), anyInt());
  }

  @Test
  public void createOrUpdate_preservesAppId_whenAlreadySet() {
    nullOutCachedSession(dao);
    var scene = new DigitalTwinScene();
    var existing = "0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506";
    scene.setAppId(existing);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var saved = dao.createOrUpdate(scene);
      assertEquals(existing, saved.getAppId(), "existing appId must be preserved");
    }
    verify(live, times(1)).save(eq(scene), anyInt());
  }

  @Test
  public void createOrUpdate_withNullSession_throwsIllegalState() {
    nullOutCachedSession(dao);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertThrows(IllegalStateException.class, () -> dao.createOrUpdate(new DigitalTwinScene()));
    }
  }

  @Test
  public void findByAppId_returnsMatchingScene() {
    nullOutCachedSession(dao);
    var scene = new DigitalTwinScene(1L);
    scene.setAppId("test-scene-app-id");

    Session live = mock(Session.class);
    when(live.loadAll(eq(DigitalTwinScene.class), any(Filter.class), anyInt()))
      .thenReturn(List.of(scene));
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var result = dao.findByAppId("test-scene-app-id");
      assertSame(scene, result);
    }
  }

  @Test
  public void findByAppId_returnsNullWhenNotFound() {
    nullOutCachedSession(dao);
    Session live = mock(Session.class);
    when(live.loadAll(eq(DigitalTwinScene.class), any(Filter.class), anyInt()))
      .thenReturn(List.of());
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertNull(dao.findByAppId("nonexistent-id"));
    }
  }

  @Test
  public void findByAppId_returnsNullForNullOrBlankId() {
    assertNull(dao.findByAppId(null));
    assertNull(dao.findByAppId(""));
    assertNull(dao.findByAppId("   "));
  }

  @Test
  public void findByAppId_withNullSession_returnsNull() {
    nullOutCachedSession(dao);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertNull(dao.findByAppId("some-id"));
    }
  }

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
