package de.dlr.shepard.v2.scenegraph.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.export.UrdfExporter;
import de.dlr.shepard.v2.scenegraph.io.CreateFrameRequestIO;
import de.dlr.shepard.v2.scenegraph.io.CreateSceneRequestIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphPermissionService;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SCENEGRAPH-PERMS-1 — REST-layer per-scene permission gate tests for
 * {@link SceneGraphRest}. Complements {@link SceneGraphRestListTest} which
 * covers the list endpoint's post-filter, and the service-layer
 * {@code SceneGraphPermissionServiceTest} which covers the walk itself.
 */
class SceneGraphRestPermsTest {

  private static final String SCENE = "01900000-0000-7000-8000-0000000000a1";
  private static final String SRC_FILE = "01900000-0000-7000-8000-0000000000b1";
  private static final String CALLER = "alice";

  private SceneGraphRest resource;
  private SceneGraphService svc;
  private SceneGraphPermissionService perms;
  private UrdfExporter exporter;
  private SecurityContext sc;
  private Principal principal;

  @BeforeEach
  void setUp() {
    resource = new SceneGraphRest();
    svc = mock(SceneGraphService.class);
    perms = mock(SceneGraphPermissionService.class);
    exporter = mock(UrdfExporter.class);
    sc = mock(SecurityContext.class);
    principal = mock(Principal.class);

    resource.sceneGraphService = svc;
    resource.scenePermissions = perms;
    resource.urdfExporter = exporter;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);

    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId(SCENE);
    scene.setSourceFileAppId(SRC_FILE);
    when(svc.findScene(SCENE)).thenReturn(scene);
    when(svc.findFramesForScene(SCENE)).thenReturn(List.of());
    when(svc.findJointsForScene(SCENE)).thenReturn(List.of());
  }

  // ── GET scene ─────────────────────────────────────────────────────────

  @Test
  void get_returns200_whenReadPermitted() {
    when(perms.isAllowed(eq(SCENE), eq(AccessType.Read), eq(CALLER), anyBoolean())).thenReturn(true);
    Response r = resource.get(SCENE, null, sc);
    assertThat(r.getStatus()).isEqualTo(200);
  }

  @Test
  void get_returns403_whenReadDenied() {
    when(perms.isAllowed(eq(SCENE), eq(AccessType.Read), eq(CALLER), anyBoolean())).thenReturn(false);
    Response r = resource.get(SCENE, null, sc);
    assertThat(r.getStatus()).isEqualTo(403);
  }

  @Test
  void get_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.get(SCENE, null, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  @Test
  void get_returns404_whenSceneMissing_evenWithoutPermCheck() {
    when(svc.findScene(SCENE)).thenReturn(null);
    Response r = resource.get(SCENE, null, sc);
    assertThat(r.getStatus()).isEqualTo(404);
    verify(perms, never()).isAllowed(anyString(), any(), anyString(), anyBoolean());
  }

  // ── POST add frame (Write gate) ────────────────────────────────────────

  @Test
  void addFrame_returns403_whenWriteDenied() {
    when(perms.isAllowed(eq(SCENE), eq(AccessType.Write), eq(CALLER), anyBoolean())).thenReturn(false);
    Response r = resource.addFrame(SCENE, new CreateFrameRequestIO(), null, sc);
    assertThat(r.getStatus()).isEqualTo(403);
    verify(svc, never()).addFrame(anyString(), any(), any());
  }

  @Test
  void addFrame_returns401_whenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.addFrame(SCENE, new CreateFrameRequestIO(), null, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  // ── POST create scene (Write on source-file's parent) ──────────────────

  @Test
  void create_returns403_whenWriteOnSourceFileDenied() {
    when(perms.canCreateFromSourceFile(eq(SRC_FILE), eq(CALLER))).thenReturn(false);
    CreateSceneRequestIO body = new CreateSceneRequestIO();
    body.setName("smoke");
    body.setSourceFileAppId(SRC_FILE);
    Response r = resource.create(body, null, sc);
    assertThat(r.getStatus()).isEqualTo(403);
    verify(svc, never()).createScene(any(), any());
  }

  @Test
  void create_handBuilt_requiresAdmin() {
    when(sc.isUserInRole(SceneGraphPermissionService.INSTANCE_ADMIN_ROLE)).thenReturn(false);
    CreateSceneRequestIO body = new CreateSceneRequestIO();
    body.setName("smoke");
    // No sourceFileAppId — hand-built.
    Response r = resource.create(body, null, sc);
    assertThat(r.getStatus()).isEqualTo(403);
    verify(svc, never()).createScene(any(), any());
  }

  @Test
  void create_handBuilt_byAdmin_succeeds() {
    when(sc.isUserInRole(SceneGraphPermissionService.INSTANCE_ADMIN_ROLE)).thenReturn(true);
    DigitalTwinScene created = new DigitalTwinScene();
    created.setAppId("new-app-id");
    when(svc.createScene(any(), any())).thenReturn(created);

    CreateSceneRequestIO body = new CreateSceneRequestIO();
    body.setName("smoke");
    Response r = resource.create(body, null, sc);
    assertThat(r.getStatus()).isEqualTo(201);
  }
}
