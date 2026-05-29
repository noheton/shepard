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
 * <p>Covers the scaffold flows (getEntityType, createOrUpdate appId-minting +
 * URDF-triple preservation, findByParentFrameAppId with non-null and null parent)
 * plus two fresh-session regression tests verifying the CHOKE-03 fix on both
 * {@code createOrUpdate} and {@code findByParentFrameAppId}.
 */
public class JointDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private JointDAO dao = new JointDAO();

  // ─── scaffold tests (DT1-PHASE-0) ───────────────────────────────────────

  @Test
  public void getEntityType_returnsJoint() {
    assertSame(Joint.class, dao.getEntityType());
  }

  @Test
  public void createOrUpdate_mintsAppId_andPreservesUrdfTriple() {
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
      verify(live).save(joint, 1);
    }
  }

  @Test
  public void findByParentFrameAppId_filtersOnParentFrameAppId_whenParentNonNull() {
    var joint = new Joint(11L);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

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
    var joint = new Joint(12L);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

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

    var joint = new Joint();

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var saved = dao.createOrUpdate(joint);
      assertNotNull(saved);
      assertNotNull(saved.getAppId(), "appId minted via live session path");
    }
    verify(live).save(eq(joint), anyInt());
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
        () -> dao.createOrUpdate(new Joint()),
        "Must throw ISE, not NPE, when session is unavailable"
      );
    }
  }

  /**
   * Simulates CHOKE-03 for the finder: cached {@code this.session} is null,
   * but the live session works. {@code findByParentFrameAppId} must return
   * results via the live session rather than NPE.
   */
  @Test
  public void findByParentFrameAppId_withNullCachedSession_usesFreshSession() {
    nullOutCachedSession(dao);

    Session live = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    var joint = new Joint(9L);
    when(live.loadAll(eq(Joint.class), org.mockito.ArgumentMatchers.any(Filter.class), eq(1)))
      .thenReturn(List.of(joint));

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var actual = dao.findByParentFrameAppId("frame-abc");
      assertEquals(1, actual.size());
    }
  }

  /**
   * When the live session from NeoConnector is also null, {@code findByParentFrameAppId}
   * must return an empty collection — not NPE — consistent with the fail-soft
   * registry rule.
   */
  @Test
  public void findByParentFrameAppId_withNullSessionFactory_returnsEmpty() {
    nullOutCachedSession(dao);

    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(null);

    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var actual = dao.findByParentFrameAppId("frame-abc");
      assertTrue(actual.isEmpty(), "Must return empty collection when session unavailable");
    }
  }

  // ─── helpers ────────────────────────────────────────────────────────────

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
}
