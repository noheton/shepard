package de.dlr.shepard.v2.scenegraph.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
 * DT1-PHASE-0 / DT1-DAO-FRESH-SESSION — unit tests for {@link JointDAO}.
 *
 * <p>Overridden methods ({@code createOrUpdate}, {@code findByParentFrameAppId})
 * are tested via {@code mockStatic(NeoConnector.class)} after clearing the
 * cached session (CHOKE-03 pattern). No inherited methods are exercised here
 * that need the old session mock.
 */
public class JointDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private JointDAO dao = new JointDAO();

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
    assertSame(Joint.class, dao.getEntityType());
  }

  @Test
  public void createOrUpdate_mintsAppId_andPreservesUrdfTriple() {
    nullOutCachedSession(dao);
    var joint = new Joint();
    joint.setName("joint_1");
    joint.setAxisX(0d);
    joint.setAxisY(0d);
    joint.setAxisZ(1d);
    joint.setLimitMin(-Math.PI);
    joint.setLimitMax(Math.PI);
    joint.setType(JointType.REVOLUTE);
    joint.setHomeAngle(0d);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

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
  public void findByParentFrameAppId_filtersOnParentFrameAppId_whenParentNonNull() {
    nullOutCachedSession(dao);
    var joint = new Joint(11L);

    Session live = mock(Session.class);
    ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
    when(live.loadAll(eq(Joint.class), filterCaptor.capture(), anyInt()))
      .thenReturn(List.of(joint));
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var actual = dao.findByParentFrameAppId("parent-frame-xyz");
      assertEquals(1, actual.size());
    }
    var filter = filterCaptor.getValue();
    assertEquals("parentFrameAppId", filter.getPropertyName());
  }

  @Test
  public void findByParentFrameAppId_filtersOnParentFrameAppId_whenParentNull() {
    nullOutCachedSession(dao);
    var joint = new Joint(12L);

    Session live = mock(Session.class);
    ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
    when(live.loadAll(eq(Joint.class), filterCaptor.capture(), anyInt()))
      .thenReturn(List.of(joint));
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      dao.findByParentFrameAppId(null);
    }
    var filter = filterCaptor.getValue();
    assertEquals("parentFrameAppId", filter.getPropertyName());
  }

  @Test
  public void findByParentFrameAppId_withNullSession_returnsEmpty() {
    nullOutCachedSession(dao);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var result = dao.findByParentFrameAppId("any-parent");
      assertNotNull(result);
      assertEquals(0, result.size());
    }
  }
}
