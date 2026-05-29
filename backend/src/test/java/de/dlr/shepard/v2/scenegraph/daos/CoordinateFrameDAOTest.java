package de.dlr.shepard.v2.scenegraph.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * DT1-DAO-FRESH-SESSION — unit tests for {@link CoordinateFrameDAO}.
 *
 * <p>Covers the scaffold flows (getEntityType, createOrUpdate appId-minting,
 * findByParentAppId with non-null and null parent, findAll) plus two
 * fresh-session regression tests verifying the CHOKE-03 fix on both
 * {@code createOrUpdate} and {@code findByParentAppId}.
 */
public class CoordinateFrameDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private CoordinateFrameDAO dao = new CoordinateFrameDAO();

  // ─── scaffold tests (DT1-PHASE-0) ───────────────────────────────────────

  @Test
  public void getEntityType_returnsCoordinateFrame() {
    assertSame(CoordinateFrame.class, dao.getEntityType());
  }

  @Test
  public void createOrUpdate_mintsAppId_andPreservesTransformScalars() {
    var frame = new CoordinateFrame();
    frame.setName("tool0");
    frame.setX(1.5d);
    frame.setRy(0.25d);
    frame.setKind(FrameKind.TCP);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var saved = dao.createOrUpdate(frame);

      assertNotNull(saved.getAppId());
      var parsed = UUID.fromString(saved.getAppId());
      assertEquals(7, parsed.version());
      assertEquals(1.5d, saved.getX(), 1e-12);
      assertEquals(0.25d, saved.getRy(), 1e-12);
      assertEquals(FrameKind.TCP, saved.getKind());
      verify(live).save(frame, 1);
    }
  }

  @Test
  public void findByParentAppId_filtersOnParentFrameAppId_whenParentNonNull() {
    var child = new CoordinateFrame(7L);
    child.setParentFrameAppId("parent-abc");

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
    when(live.loadAll(eq(CoordinateFrame.class), filterCaptor.capture(), eq(1)))
      .thenReturn(List.of(child));

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var actual = dao.findByParentAppId("parent-abc");

      assertEquals(1, actual.size());
      var filter = filterCaptor.getValue();
      assertEquals("parentFrameAppId", filter.getPropertyName());
    }
  }

  @Test
  public void findByParentAppId_filtersOnParentFrameAppId_whenParentNull() {
    var root = new CoordinateFrame(1L);
    root.setParentFrameAppId(null);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
    when(live.loadAll(eq(CoordinateFrame.class), filterCaptor.capture(), eq(1)))
      .thenReturn(List.of(root));

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var actual = dao.findByParentAppId(null);

      assertEquals(1, actual.size());
      var filter = filterCaptor.getValue();
      assertEquals("parentFrameAppId", filter.getPropertyName());
    }
  }

  @Test
  public void findAll_delegatesToSession() {
    var a = new CoordinateFrame(1L);
    var b = new CoordinateFrame(2L);
    when(session.loadAll(CoordinateFrame.class, 1)).thenReturn(List.of(a, b));

    var actual = dao.findAll();

    assertEquals(2, actual.size());
  }

  // ─── fresh-session regression tests (DT1-DAO-FRESH-SESSION) ────────────

  /**
   * Simulates CHOKE-03: cached {@code this.session} is null, but the live
   * session from NeoConnector is working. {@code createOrUpdate} must
   * persist successfully via the live session.
   */
  @Test
  public void createOrUpdate_withNullCachedSession_persistsViaLiveSession() {
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var frame = new CoordinateFrame();

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var saved = dao.createOrUpdate(frame);
      assertNotNull(saved);
      assertNotNull(saved.getAppId(), "appId minted via live session path");
    }
    verify(live).save(eq(frame), anyInt());
  }

  /**
   * When the live session from NeoConnector is also null, {@code createOrUpdate}
   * must throw {@code IllegalStateException} — not NPE.
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
        () -> dao.createOrUpdate(new CoordinateFrame()),
        "Must throw ISE, not NPE, when session is unavailable"
      );
    }
  }

  /**
   * Simulates CHOKE-03 for the finder: cached {@code this.session} is null,
   * but the live session works. {@code findByParentAppId} must return results
   * via the live session rather than NPE.
   */
  @Test
  public void findByParentAppId_withNullCachedSession_usesFreshSession() {
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var child = new CoordinateFrame(5L);
    child.setParentFrameAppId("parent-xyz");
    when(live.loadAll(eq(CoordinateFrame.class), org.mockito.ArgumentMatchers.any(Filter.class), eq(1)))
      .thenReturn(List.of(child));

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var actual = dao.findByParentAppId("parent-xyz");
      assertEquals(1, actual.size());
    }
  }

  /**
   * When the live session from NeoConnector is also null, {@code findByParentAppId}
   * must return an empty collection — not NPE — consistent with the fail-soft
   * registry rule.
   */
  @Test
  public void findByParentAppId_withNullSessionFactory_returnsEmpty() {
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var actual = dao.findByParentAppId("parent-xyz");
      assertTrue(actual.isEmpty(), "Must return empty collection when session unavailable");
    }
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private static void nullOutCachedSession(CoordinateFrameDAO dao) {
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
