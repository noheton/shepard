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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.entities.JointType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * DT1-DAO-FRESH-SESSION — unit tests for {@link JointDAO}.
 *
 * <p>Verifies that {@code createOrUpdate}, {@code findAll}, and
 * {@code findByParentFrameAppId} all use a live OGM session fetched
 * per call (CHOKE-03 fix). Mirrors the
 * {@code SqlTimeseriesConfigDAOTest} test shape.
 */
public class JointDAOTest {

  /**
   * Reflectively null out the cached {@code session} field inherited
   * from {@code GenericDAO} to simulate the startup race condition.
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

  @Test
  public void getEntityType_returnsJoint() {
    assertSame(Joint.class, new JointDAO().getEntityType());
  }

  @Test
  public void createOrUpdate_mintsAppId_andPreservesUrdfTriple() {
    JointDAO dao = new JointDAO();
    nullOutCachedSession(dao);

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
      var saved = dao.createOrUpdate(joint);

      assertNotNull(saved.getAppId());
      var parsed = UUID.fromString(saved.getAppId());
      assertEquals(7, parsed.version());
      assertEquals(1d, saved.getAxisZ(), 1e-12);
      assertEquals(Math.PI, saved.getLimitMax(), 1e-12);
      assertEquals(JointType.REVOLUTE, saved.getType());
    }
    verify(live, times(1)).save(joint, 1);
  }

  @Test
  public void createOrUpdate_withNullSessionFactory_throwsIllegalState() {
    JointDAO dao = new JointDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertThrows(IllegalStateException.class, () -> dao.createOrUpdate(new Joint()));
    }
  }

  @Test
  public void findAll_delegatesToLiveSession() {
    JointDAO dao = new JointDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var a = new Joint(1L);
    var b = new Joint(2L);
    when(live.loadAll(Joint.class, 1)).thenReturn(List.of(a, b));

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var actual = dao.findAll();
      assertEquals(2, actual.size());
    }
    verify(live, times(1)).loadAll(eq(Joint.class), anyInt());
  }

  @Test
  public void findAll_withNullSessionFactory_returnsEmptyList() {
    JointDAO dao = new JointDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertTrue(dao.findAll().isEmpty());
    }
  }

  @Test
  public void findByParentFrameAppId_filtersOnParentFrameAppId_whenParentNonNull() {
    JointDAO dao = new JointDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var joint = new Joint(11L);
    ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
    when(live.loadAll(eq(Joint.class), filterCaptor.capture(), eq(1)))
      .thenReturn(List.of(joint));

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var actual = dao.findByParentFrameAppId("parent-frame-xyz");

      assertEquals(1, actual.size());
      var filter = filterCaptor.getValue();
      assertEquals("parentFrameAppId", filter.getPropertyName());
    }
  }

  @Test
  public void findByParentFrameAppId_filtersOnParentFrameAppId_whenParentNull() {
    JointDAO dao = new JointDAO();
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var joint = new Joint(12L);
    ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
    when(live.loadAll(eq(Joint.class), filterCaptor.capture(), eq(1)))
      .thenReturn(List.of(joint));

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      dao.findByParentFrameAppId(null);

      var filter = filterCaptor.getValue();
      assertEquals("parentFrameAppId", filter.getPropertyName());
    }
  }

  @Test
  public void findByParentFrameAppId_withNullSessionFactory_returnsEmptyList() {
    JointDAO dao = new JointDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertTrue(dao.findByParentFrameAppId("any").isEmpty());
    }
  }
}
