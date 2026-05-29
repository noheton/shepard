package de.dlr.shepard.v2.scenegraph.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.neo4j.ogm.session.Session;

/**
 * DT1-PHASE-0 — unit tests for {@link DigitalTwinSceneDAO}.
 *
 * <p>Mocks the inherited {@code session} field on {@link
 * de.dlr.shepard.common.neo4j.daos.GenericDAO} to exercise the four
 * core flows: create-with-mint, create-preserves-appId, findAll,
 * findByNeo4jId.
 */
public class DigitalTwinSceneDAOTest extends BaseTestCase {

  @Mock
  private Session session;

  @InjectMocks
  private DigitalTwinSceneDAO dao = new DigitalTwinSceneDAO();

  @Test
  public void getEntityType_returnsDigitalTwinScene() {
    assertSame(DigitalTwinScene.class, dao.getEntityType());
  }

  @Test
  public void createOrUpdate_mintsAppId_whenNull() {
    var scene = new DigitalTwinScene();
    scene.setName("test-scene");
    assertNull(scene.getAppId(), "precondition: appId starts null");

    var saved = dao.createOrUpdate(scene);

    assertNotNull(saved.getAppId(), "appId should be populated after save");
    assertEquals(36, saved.getAppId().length(), "appId should be canonical 36-char UUID");
    var parsed = UUID.fromString(saved.getAppId());
    assertEquals(7, parsed.version(), "L2a requires UUID v7");
    verify(session).save(scene, 1);
  }

  @Test
  public void createOrUpdate_preservesAppId_whenAlreadySet() {
    var scene = new DigitalTwinScene();
    var existing = "0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506";
    scene.setAppId(existing);

    var saved = dao.createOrUpdate(scene);

    assertEquals(existing, saved.getAppId(), "existing appId must be preserved");
    verify(session).save(scene, 1);
  }

  @Test
  public void findAll_delegatesToSession() {
    var a = new DigitalTwinScene(1L);
    var b = new DigitalTwinScene(2L);
    when(session.loadAll(DigitalTwinScene.class, 1)).thenReturn(List.of(a, b));

    var actual = dao.findAll();

    assertTrue(actual.containsAll(List.of(a, b)));
    assertEquals(2, actual.size());
  }

  @Test
  public void findByNeo4jId_delegatesToSession() {
    var scene = new DigitalTwinScene(42L);
    when(session.load(eq(DigitalTwinScene.class), eq(42L), eq(1))).thenReturn(scene);

    var actual = dao.findByNeo4jId(42L);

    assertSame(scene, actual);
  }
}
