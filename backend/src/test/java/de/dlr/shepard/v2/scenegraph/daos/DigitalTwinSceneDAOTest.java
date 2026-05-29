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
 * <p>Covers the four scaffold flows (getEntityType, findAll,
 * findByNeo4jId, and createOrUpdate's appId-minting / appId-preservation)
 * plus two fresh-session regression tests that verify CHOKE-03 is fixed:
 * {@code createOrUpdate} must use a live session from
 * {@code NeoConnector.getInstance().getNeo4jSession()} rather than the
 * cached {@code this.session} inherited from {@code GenericDAO}.
 */
public class DigitalTwinSceneDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private DigitalTwinSceneDAO dao = new DigitalTwinSceneDAO();

  // ─── scaffold tests (DT1-PHASE-0) ───────────────────────────────────────

  @Test
  public void getEntityType_returnsDigitalTwinScene() {
    assertSame(DigitalTwinScene.class, dao.getEntityType());
  }

  @Test
  public void createOrUpdate_mintsAppId_whenNull() {
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
      verify(live).save(scene, 1);
    }
  }

  @Test
  public void createOrUpdate_preservesAppId_whenAlreadySet() {
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
      verify(live).save(scene, 1);
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

  // ─── fresh-session regression tests (DT1-DAO-FRESH-SESSION) ────────────

  /**
   * Simulates CHOKE-03: the bean was constructed before SessionFactory was
   * ready, so the cached {@code this.session} field is null. The override
   * must fall through to the live session returned by NeoConnector and
   * persist successfully without NPE.
   */
  @Test
  public void createOrUpdate_withNullCachedSession_persistsViaLiveSession() {
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var scene = new DigitalTwinScene();

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var saved = dao.createOrUpdate(scene);
      assertNotNull(saved);
      assertNotNull(saved.getAppId(), "appId minted via live session path");
    }
    verify(live).save(eq(scene), anyInt());
  }

  /**
   * When {@code NeoConnector.getInstance().getNeo4jSession()} returns
   * {@code null} (SessionFactory not yet ready, live session unavailable),
   * {@code createOrUpdate} must throw {@code IllegalStateException} rather
   * than NPE — so callers get a diagnostic error, not a confusing NPE.
   */
  @Test
  public void createOrUpdate_withNullSessionFactory_throwsIllegalStateNotNpe() {
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertThrows(
        IllegalStateException.class,
        () -> dao.createOrUpdate(new DigitalTwinScene()),
        "Must throw ISE, not NPE, when session is unavailable"
      );
    }
  }

  // ─── helpers ────────────────────────────────────────────────────────────

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
}
