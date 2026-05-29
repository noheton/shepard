package de.dlr.shepard.v2.scenegraph.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * DT1-PHASE-0 — unit tests for {@link CoordinateFrameDAO}.
 */
public class CoordinateFrameDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private CoordinateFrameDAO dao = new CoordinateFrameDAO();

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
    assertNull(frame.getAppId());

    var saved = dao.createOrUpdate(frame);

    assertNotNull(saved.getAppId());
    var parsed = UUID.fromString(saved.getAppId());
    assertEquals(7, parsed.version());
    assertEquals(1.5d, saved.getX(), 1e-12);
    assertEquals(0.25d, saved.getRy(), 1e-12);
    assertEquals(FrameKind.TCP, saved.getKind());
    verify(session).save(frame, 1);
  }

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
