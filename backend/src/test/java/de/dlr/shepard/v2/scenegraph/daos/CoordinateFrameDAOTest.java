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
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * DT1-DAO-FRESH-SESSION — unit tests for {@link CoordinateFrameDAO}.
 *
 * <p>Verifies that {@link CoordinateFrameDAO#createOrUpdate} and
 * {@link CoordinateFrameDAO#findByParentAppId} no longer rely on the
 * cached {@code this.session} field inherited from {@code GenericDAO}
 * (CHOKE-03 / JupyterConfig pattern). Mirrors the pattern established
 * in {@code SqlTimeseriesConfigDAOTest} and {@code InstanceRorConfigDAOTest}.
 */
public class CoordinateFrameDAOTest {

  /**
   * Reflectively clear the cached {@code session} field that
   * {@code GenericDAO}'s constructor would have eagerly assigned.
   */
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

  @Test
  public void getEntityType_returnsCoordinateFrame() {
    assertSame(CoordinateFrame.class, new CoordinateFrameDAO().getEntityType());
  }

  // --- createOrUpdate — fresh-session path ---

  @Test
  public void createOrUpdate_mintsAppId_andPreservesTransformScalars() {
    CoordinateFrameDAO dao = new CoordinateFrameDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var frame = new CoordinateFrame();
    frame.setName("tool0");
    frame.setX(1.5d);
    frame.setRy(0.25d);
    frame.setKind(FrameKind.TCP);
    assertNull(frame.getAppId());

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      var saved = dao.createOrUpdate(frame);

      assertNotNull(saved.getAppId());
      var parsed = UUID.fromString(saved.getAppId());
      assertEquals(7, parsed.version());
      assertEquals(1.5d, saved.getX(), 1e-12);
      assertEquals(0.25d, saved.getRy(), 1e-12);
      assertEquals(FrameKind.TCP, saved.getKind());
    }
    verify(live, times(1)).save(eq(frame), anyInt());
  }

  @Test
  public void createOrUpdate_withNullSessionFactory_throwsIllegalStateNotNpe() {
    CoordinateFrameDAO dao = new CoordinateFrameDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertThrows(IllegalStateException.class, () -> dao.createOrUpdate(new CoordinateFrame()));
    }
  }

  // --- findByParentAppId — fresh-session path ---

  @Test
  public void findByParentAppId_filtersOnParentFrameAppId_whenParentNonNull() {
    CoordinateFrameDAO dao = new CoordinateFrameDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var child = new CoordinateFrame(7L);
    child.setParentFrameAppId("parent-abc");

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
    CoordinateFrameDAO dao = new CoordinateFrameDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var root = new CoordinateFrame(1L);
    root.setParentFrameAppId(null);

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
  public void findByParentAppId_withNullSessionFactory_returnsEmptyNotNpe() {
    CoordinateFrameDAO dao = new CoordinateFrameDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      var actual = dao.findByParentAppId("any-parent");

      assertNotNull(actual);
      assertTrue(actual.isEmpty());
    }
  }
}
