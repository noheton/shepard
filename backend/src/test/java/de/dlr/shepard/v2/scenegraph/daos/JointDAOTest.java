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
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.entities.JointType;
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
 * DT1-DAO-FRESH-SESSION — unit tests for {@link JointDAO}.
 *
 * <p>{@code createOrUpdate_*} tests use {@code mockStatic(NeoConnector.class)} to
 * verify the fresh-session override (CHOKE-03 fix). Finder tests still rely on
 * {@code @InjectMocks} + {@code @Mock Session} because {@code findMatching} /
 * {@code findAll} delegate to the base-class {@code session} field.
 */
public class JointDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private JointDAO dao = new JointDAO();

  // ---- helpers ---------------------------------------------------------------

  /**
   * Reflectively null out the cached {@code session} field that
   * {@code GenericDAO}'s constructor eagerly assigns. Simulates the CHOKE-03
   * startup race where the bean is built before the OGM {@code SessionFactory}
   * is ready.
   */
  private static void nullOutCachedSession(JointDAO dao) {
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
  public void getEntityType_returnsJoint() {
    assertSame(Joint.class, dao.getEntityType());
  }

  // ---- createOrUpdate — fresh-session path (DT1-DAO-FRESH-SESSION) -----------

  @Test
  public void createOrUpdate_mintsAppId_andPreservesUrdfTriple() {
    JointDAO freshDao = new JointDAO();
    nullOutCachedSession(freshDao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var joint = new Joint();
    joint.setName("joint_1");
    joint.setAxisX(0d);
    joint.setAxisY(0d);
    joint.setAxisZ(1d);
    joint.setLimitMin(-Math.PI);
    joint.setLimitMax(Math.PI);
    joint.setType(JointType.REVOLUTE);
    joint.setHomeAngle(0d);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      var saved = freshDao.createOrUpdate(joint);

      assertNotNull(saved.getAppId());
      var parsed = UUID.fromString(saved.getAppId());
      assertEquals(7, parsed.version());
      assertEquals(1d, saved.getAxisZ(), 1e-12);
      assertEquals(Math.PI, saved.getLimitMax(), 1e-12);
      assertEquals(JointType.REVOLUTE, saved.getType());
      verify(live, times(1)).save(eq(joint), anyInt());
    }
  }

  @Test
  public void createOrUpdate_returnsNull_whenEntityIsNull() {
    JointDAO freshDao = new JointDAO();
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
    JointDAO freshDao = new JointDAO();
    nullOutCachedSession(freshDao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      assertThrows(IllegalStateException.class, () -> freshDao.createOrUpdate(new Joint()));
    }
  }

  // ---- finder methods (still use inherited this.session) ---------------------

  @Test
  public void findByParentFrameAppId_filtersOnParentFrameAppId_whenParentNonNull() {
    var joint = new Joint(11L);
    ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
    when(session.loadAll(eq(Joint.class), filterCaptor.capture(), eq(1)))
      .thenReturn(List.of(joint));

    var actual = dao.findByParentFrameAppId("parent-frame-xyz");

    assertEquals(1, actual.size());
    var filter = filterCaptor.getValue();
    assertEquals("parentFrameAppId", filter.getPropertyName());
  }

  @Test
  public void findByParentFrameAppId_filtersOnParentFrameAppId_whenParentNull() {
    var joint = new Joint(12L);
    ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
    when(session.loadAll(eq(Joint.class), filterCaptor.capture(), eq(1)))
      .thenReturn(List.of(joint));

    dao.findByParentFrameAppId(null);

    var filter = filterCaptor.getValue();
    assertEquals("parentFrameAppId", filter.getPropertyName());
  }
}
