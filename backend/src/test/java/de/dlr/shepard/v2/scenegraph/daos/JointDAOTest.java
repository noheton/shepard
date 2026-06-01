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
 * <p>Verifies that {@link JointDAO#createOrUpdate} and
 * {@link JointDAO#findByParentFrameAppId} no longer rely on the cached
 * {@code this.session} field inherited from {@code GenericDAO}
 * (CHOKE-03 / JupyterConfig pattern). Mirrors the pattern established
 * in {@code SqlTimeseriesConfigDAOTest} and {@code InstanceRorConfigDAOTest}.
 */
public class JointDAOTest {

  /**
   * Reflectively clear the cached {@code session} field that
   * {@code GenericDAO}'s constructor would have eagerly assigned.
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

  // --- createOrUpdate — fresh-session path ---

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
    verify(live, times(1)).save(eq(joint), anyInt());
  }

  @Test
  public void createOrUpdate_withNullSessionFactory_throwsIllegalStateNotNpe() {
    JointDAO dao = new JointDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertThrows(IllegalStateException.class, () -> dao.createOrUpdate(new Joint()));
    }
  }

  // --- findByParentFrameAppId — fresh-session path ---

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
  public void findByParentFrameAppId_withNullSessionFactory_returnsEmptyNotNpe() {
    JointDAO dao = new JointDAO();
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      var actual = dao.findByParentFrameAppId("any-parent");

      assertNotNull(actual);
      assertTrue(actual.isEmpty());
    }
  }
}
