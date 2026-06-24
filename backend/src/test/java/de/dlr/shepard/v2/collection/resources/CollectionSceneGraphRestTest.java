package de.dlr.shepard.v2.collection.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.collection.daos.CollectionSceneGraphLinkDAO;
import de.dlr.shepard.v2.collection.io.CollectionSceneGraphLinkIO;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphPermissionService;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * COLL-SCENE-1 — happy-path + permission-denial + missing-entity coverage
 * for the Collection ↔ DigitalTwinScene link surface. Mirrors the shape
 * of {@code CollectionPropertiesRestTest} (same auth + DAO + permissions
 * pattern) plus the two-sided permission gate this resource adds for PUT.
 */
class CollectionSceneGraphRestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000020";
  static final long COLL_OGM_ID = 77L;
  static final String SCENE_APP_ID = "018f9c5a-7e26-7000-a000-000000000021";
  static final String CALLER = "alice";

  @Mock CollectionSceneGraphLinkDAO linkDAO;
  @Mock PermissionsService permissionsService;
  @Mock SceneGraphService sceneGraphService;
  @Mock SceneGraphPermissionService scenePermissions;
  @Mock SecurityContext securityContext;
  @Mock Principal principal;

  CollectionSceneGraphRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionSceneGraphRest();
    resource.linkDAO = linkDAO;
    resource.permissionsService = permissionsService;
    resource.sceneGraphService = sceneGraphService;
    resource.scenePermissions = scenePermissions;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  // ── GET ────────────────────────────────────────────────────────────────────

  @Test
  void getReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void getReturns404WhenCollectionMissing() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessTypeAllowedForUser(anyLong(), any(), any(), anyLong());
  }

  @Test
  void getReturns403WhenCallerLacksReadOnCollection() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);
    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
    verify(linkDAO, never()).findLinkedSceneAppId(any());
  }

  @Test
  void getReturns404WhenNoSceneLinked() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(linkDAO.findLinkedSceneAppId(COLL_APP_ID)).thenReturn(Optional.empty());

    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getReturns200WithSceneIdentityWhenLinked() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(linkDAO.findLinkedSceneAppId(COLL_APP_ID)).thenReturn(Optional.of(SCENE_APP_ID));

    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId(SCENE_APP_ID);
    scene.setName("MFFD robot cell");
    scene.setDescription("desc");
    scene.setRootFrameAppId("root-frame-app-id");
    scene.setSourceFileAppId("source-file-app-id");
    when(sceneGraphService.findScene(SCENE_APP_ID)).thenReturn(scene);
    when(sceneGraphService.findFramesForScene(SCENE_APP_ID)).thenReturn(List.of());
    when(sceneGraphService.findJointsForScene(SCENE_APP_ID)).thenReturn(List.of());

    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    var io = (CollectionSceneGraphLinkIO) r.getEntity();
    assertNotNull(io);
    assertEquals(SCENE_APP_ID, io.getSceneGraphAppId());
    assertEquals("MFFD robot cell", io.getName());
    assertEquals("desc", io.getDescription());
    assertEquals("root-frame-app-id", io.getRootFrameAppId());
    assertEquals(0L, io.getFrameCount().longValue());
    assertEquals(0L, io.getJointCount().longValue());
  }

  @Test
  void getReturns200WhenLinkedSceneEntityIsMissing() {
    // Dangling pointer: the scene was hard-deleted out from under the
    // Collection's :sceneGraphAppId. Resource still returns the appId so
    // the UI renders EntityNotFound for that band rather than 500.
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(linkDAO.findLinkedSceneAppId(COLL_APP_ID)).thenReturn(Optional.of(SCENE_APP_ID));
    when(sceneGraphService.findScene(SCENE_APP_ID)).thenReturn(null);

    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    var io = (CollectionSceneGraphLinkIO) r.getEntity();
    assertEquals(SCENE_APP_ID, io.getSceneGraphAppId());
  }

  // ── PUT ────────────────────────────────────────────────────────────────────

  @Test
  void putReturns400WhenBodyMissingSceneId() {
    var body = new CollectionSceneGraphLinkIO();
    Response r = resource.link(COLL_APP_ID, body, securityContext);
    assertEquals(400, r.getStatus());
    verify(linkDAO, never()).link(any(), any());
  }

  @Test
  void putReturns400WhenBodyNull() {
    Response r = resource.link(COLL_APP_ID, null, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void putReturns404WhenCollectionMissing() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());
    var body = new CollectionSceneGraphLinkIO();
    body.setSceneGraphAppId(SCENE_APP_ID);
    Response r = resource.link(COLL_APP_ID, body, securityContext);
    assertEquals(404, r.getStatus());
    verify(linkDAO, never()).link(any(), any());
  }

  @Test
  void putReturns403WhenCallerLacksWriteOnCollection() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(false);
    var body = new CollectionSceneGraphLinkIO();
    body.setSceneGraphAppId(SCENE_APP_ID);

    Response r = resource.link(COLL_APP_ID, body, securityContext);
    assertEquals(403, r.getStatus());
    verify(sceneGraphService, never()).findScene(any());
  }

  @Test
  void putReturns404WhenTargetSceneMissing() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(sceneGraphService.findScene(SCENE_APP_ID)).thenReturn(null);
    var body = new CollectionSceneGraphLinkIO();
    body.setSceneGraphAppId(SCENE_APP_ID);

    Response r = resource.link(COLL_APP_ID, body, securityContext);
    assertEquals(404, r.getStatus());
    verify(scenePermissions, never()).isAllowed(any(), any(), any(), anyBoolean());
  }

  @Test
  void putReturns403WhenCallerLacksReadOnTargetScene() {
    // Two-sided gate: writer on Collection cannot link a scene they cannot
    // themselves read (prevents private-scene blind-link).
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId(SCENE_APP_ID);
    when(sceneGraphService.findScene(SCENE_APP_ID)).thenReturn(scene);
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID), eq(AccessType.Read), eq(CALLER), anyBoolean()))
      .thenReturn(false);
    var body = new CollectionSceneGraphLinkIO();
    body.setSceneGraphAppId(SCENE_APP_ID);

    Response r = resource.link(COLL_APP_ID, body, securityContext);
    assertEquals(403, r.getStatus());
    verify(linkDAO, never()).link(any(), any());
  }

  @Test
  void putReturns200OnHappyPath() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    // Re-entry into GET requires Read too — same OGM id.
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId(SCENE_APP_ID);
    scene.setName("MFFD robot cell");
    when(sceneGraphService.findScene(SCENE_APP_ID)).thenReturn(scene);
    when(scenePermissions.isAllowed(eq(SCENE_APP_ID), eq(AccessType.Read), eq(CALLER), anyBoolean()))
      .thenReturn(true);
    when(linkDAO.link(COLL_APP_ID, SCENE_APP_ID)).thenReturn(true);
    when(linkDAO.findLinkedSceneAppId(COLL_APP_ID)).thenReturn(Optional.of(SCENE_APP_ID));
    when(sceneGraphService.findFramesForScene(SCENE_APP_ID)).thenReturn(List.of());
    when(sceneGraphService.findJointsForScene(SCENE_APP_ID)).thenReturn(List.of());

    var body = new CollectionSceneGraphLinkIO();
    body.setSceneGraphAppId(SCENE_APP_ID);
    Response r = resource.link(COLL_APP_ID, body, securityContext);

    assertEquals(200, r.getStatus());
    var io = (CollectionSceneGraphLinkIO) r.getEntity();
    assertNotNull(io);
    assertEquals(SCENE_APP_ID, io.getSceneGraphAppId());
    assertEquals("MFFD robot cell", io.getName());
    verify(linkDAO).link(COLL_APP_ID, SCENE_APP_ID);
  }

  // ── DELETE ─────────────────────────────────────────────────────────────────

  @Test
  void deleteReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.unlink(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void deleteReturns404WhenCollectionMissing() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.unlink(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(linkDAO, never()).unlink(any());
  }

  @Test
  void deleteReturns403WhenCallerLacksWriteOnCollection() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(false);
    Response r = resource.unlink(COLL_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
    verify(linkDAO, never()).unlink(any());
  }

  @Test
  void deleteReturns204OnHappyPath() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(linkDAO.unlink(COLL_APP_ID)).thenReturn(true);
    Response r = resource.unlink(COLL_APP_ID, securityContext);
    assertEquals(204, r.getStatus());
    verify(linkDAO).unlink(COLL_APP_ID);
  }

  @Test
  void deleteIsIdempotentWhenAlreadyUnlinked() {
    // DAO.unlink returns true if the Collection exists (no link to drop is
    // still a successful 204).
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(linkDAO.unlink(COLL_APP_ID)).thenReturn(true);
    Response r = resource.unlink(COLL_APP_ID, securityContext);
    assertEquals(204, r.getStatus());
  }
}
