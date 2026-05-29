package de.dlr.shepard.v2.scenegraph.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.entities.JointType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * DT1-PHASE-0 — unit tests for {@link JointDAO}.
 */
public class JointDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private JointDAO dao = new JointDAO();

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

    var saved = dao.createOrUpdate(joint);

    assertNotNull(saved.getAppId());
    var parsed = UUID.fromString(saved.getAppId());
    assertEquals(7, parsed.version());
    assertEquals(1d, saved.getAxisZ(), 1e-12);
    assertEquals(Math.PI, saved.getLimitMax(), 1e-12);
    assertEquals(JointType.REVOLUTE, saved.getType());
    verify(session).save(joint, 1);
  }

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
