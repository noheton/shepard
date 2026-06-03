package de.dlr.shepard.v2.scenegraph.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
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
 * <p>{@code createOrUpdate_*} tests use {@code mockStatic(NeoConnector.class)} to
 * verify the fresh-session override (CHOKE-03 fix). Finder tests still rely on
 * {@code @InjectMocks} + {@code @Mock Session} because {@code findMatching} /
 * {@code findAll} delegate to the base-class {@code session} field.
 */
public class CoordinateFrameDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private CoordinateFrameDAO dao = new CoordinateFrameDAO();

  // ---- helpers ---------------------------------------------------------------

  /**
   * Reflectively null out the cached {@code session} field that
   * {@code GenericDAO}'s constructor eagerly assigns. Simulates the CHOKE-03
   * startup race where the bean is built before the OGM {@code SessionFactory}
   * is ready.
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

  // ---- entity-type -----------------------------------------------------------

  @Test
  public void getEntityType_returnsCoordinateFrame() {
    assertSame(CoordinateFrame.class, dao.getEntityType());
  }

  // ---- createOrUpdate — fresh-session path (DT1-DAO-FRESH-SESSION) -----------

  @Test
  public void createOrUpdate_mintsAppId_andPreservesTransformScalars() {
    CoordinateFrameDAO freshDao = new CoordinateFrameDAO();
    nullOutCachedSession(freshDao);

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

      var saved = freshDao.createOrUpdate(frame);

      assertNotNull(saved.getAppId());
      var parsed = UUID.fromString(saved.getAppId());
      assertEquals(7, parsed.version());
      assertEquals(1.5d, saved.getX(), 1e-12);
      assertEquals(0.25d, saved.getRy(), 1e-12);
      assertEquals(FrameKind.TCP, saved.getKind());
      verify(live, times(1)).save(eq(frame), anyInt());
    }
  }

  @Test
  public void createOrUpdate_returnsNull_whenEntityIsNull() {
    CoordinateFrameDAO freshDao = new CoordinateFrameDAO();
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
    CoordinateFrameDAO freshDao = new CoordinateFrameDAO();
    nullOutCachedSession(freshDao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      assertThrows(IllegalStateException.class, () -> freshDao.createOrUpdate(new CoordinateFrame()));
    }
  }

  // ---- finder methods (still use inherited this.session) ---------------------

  @Test
  public void findByParentAppId_filtersOnParentFrameAppId_whenParentNonNull() {
    var child = new CoordinateFrame(7L);
    child.setParentFrameAppId("parent-abc");

    ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
    when(session.loadAll(eq(CoordinateFrame.class), filterCaptor.capture(), eq(1)))
      .thenReturn(List.of(child));

    var actual = dao.findByParentAppId("parent-abc");

    assertEquals(1, actual.size());
    var filter = filterCaptor.getValue();
    assertEquals("parentFrameAppId", filter.getPropertyName());
  }

  @Test
  public void findByParentAppId_filtersOnParentFrameAppId_whenParentNull() {
    var root = new CoordinateFrame(1L);
    root.setParentFrameAppId(null);

    ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
    when(session.loadAll(eq(CoordinateFrame.class), filterCaptor.capture(), eq(1)))
      .thenReturn(List.of(root));

    var actual = dao.findByParentAppId(null);

    assertEquals(1, actual.size());
    var filter = filterCaptor.getValue();
    assertEquals("parentFrameAppId", filter.getPropertyName());
  }

  @Test
  public void findAll_delegatesToSession() {
    var a = new CoordinateFrame(1L);
    var b = new CoordinateFrame(2L);
    when(session.loadAll(CoordinateFrame.class, 1)).thenReturn(List.of(a, b));

    var actual = dao.findAll();

    assertEquals(2, actual.size());
  }
}
