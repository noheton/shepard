package de.dlr.shepard.v2.scenegraph.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.entities.JointType;
import de.dlr.shepard.v2.scenegraph.io.CoordinateFrameIO;
import de.dlr.shepard.v2.scenegraph.io.CreateFrameIO;
import de.dlr.shepard.v2.scenegraph.io.CreateJointIO;
import de.dlr.shepard.v2.scenegraph.io.CreateSceneIO;
import de.dlr.shepard.v2.scenegraph.io.DigitalTwinSceneIO;
import de.dlr.shepard.v2.scenegraph.io.JointIO;
import de.dlr.shepard.v2.scenegraph.io.PatchFrameIO;
import de.dlr.shepard.v2.scenegraph.io.SceneGraphIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * SCENEGRAPH-REST-1 — unit tests for {@link SceneGraphRest}.
 *
 * <p>All tests use Mockito field injection. No CDI context or
 * Neo4j connection is started.
 */
class SceneGraphRestTest {

  static final String SCENE_APP_ID = "scene-001";
  static final String FRAME_APP_ID = "frame-001";
  static final String JOINT_APP_ID = "joint-001";
  static final String CALLER = "alice";

  @Mock
  SceneGraphService sceneGraphService;

  @Mock
  ContainerRequestContext requestContext;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  SceneGraphRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new SceneGraphRest();
    resource.sceneGraphService = sceneGraphService;
    resource.requestContext = requestContext;

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  // ─── create scene ────────────────────────────────────────────────────────────

  @Test
  void createScene_returns201WithScene() {
    DigitalTwinScene scene = scene(SCENE_APP_ID, "MyScene");
    when(sceneGraphService.createScene(any(), eq(CALLER))).thenReturn(scene);

    Response r = resource.createScene(new CreateSceneIO("MyScene", null, null), sc);

    assertThat(r.getStatus()).isEqualTo(201);
    DigitalTwinSceneIO io = (DigitalTwinSceneIO) r.getEntity();
    assertThat(io.appId()).isEqualTo(SCENE_APP_ID);
    assertThat(io.name()).isEqualTo("MyScene");
  }

  @Test
  void createScene_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.createScene(null, sc).getStatus()).isEqualTo(401);
    verify(sceneGraphService, never()).createScene(any(), any());
  }

  @Test
  void createScene_acceptsNullBody() {
    DigitalTwinScene scene = scene(SCENE_APP_ID, null);
    when(sceneGraphService.createScene(eq(null), eq(CALLER))).thenReturn(scene);

    Response r = resource.createScene(null, sc);
    assertThat(r.getStatus()).isEqualTo(201);
  }

  // ─── get scene ───────────────────────────────────────────────────────────────

  @Test
  void getScene_returns200WithSceneGraph() {
    SceneGraphIO graph = new SceneGraphIO(
      new DigitalTwinSceneIO(scene(SCENE_APP_ID, "MyScene")),
      List.of(),
      List.of()
    );
    when(sceneGraphService.getScene(SCENE_APP_ID)).thenReturn(graph);

    Response r = resource.getScene(SCENE_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    SceneGraphIO result = (SceneGraphIO) r.getEntity();
    assertThat(result.scene().appId()).isEqualTo(SCENE_APP_ID);
    assertThat(result.frames()).isEmpty();
    assertThat(result.joints()).isEmpty();
  }

  @Test
  void getScene_returns404WhenMissing() {
    when(sceneGraphService.getScene(SCENE_APP_ID)).thenReturn(null);
    assertThat(resource.getScene(SCENE_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void getScene_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.getScene(SCENE_APP_ID, sc).getStatus()).isEqualTo(401);
  }

  // ─── add frame ───────────────────────────────────────────────────────────────

  @Test
  void addFrame_returns201WithFrame() {
    CoordinateFrame frame = frame(FRAME_APP_ID, "base_link", null);
    when(sceneGraphService.addFrame(eq(SCENE_APP_ID), any(), eq(CALLER))).thenReturn(frame);

    CreateFrameIO body = new CreateFrameIO("base_link", null, 0, 0, 0, 0, 0, 0, "BASE");
    Response r = resource.addFrame(SCENE_APP_ID, body, sc);

    assertThat(r.getStatus()).isEqualTo(201);
    CoordinateFrameIO io = (CoordinateFrameIO) r.getEntity();
    assertThat(io.appId()).isEqualTo(FRAME_APP_ID);
    assertThat(io.name()).isEqualTo("base_link");
  }

  @Test
  void addFrame_returns404WhenSceneMissing() {
    when(sceneGraphService.addFrame(eq(SCENE_APP_ID), any(), eq(CALLER))).thenReturn(null);

    CreateFrameIO body = new CreateFrameIO("f", null, 0, 0, 0, 0, 0, 0, null);
    assertThat(resource.addFrame(SCENE_APP_ID, body, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void addFrame_returns400WhenBodyNull() {
    assertThat(resource.addFrame(SCENE_APP_ID, null, sc).getStatus()).isEqualTo(400);
    verify(sceneGraphService, never()).addFrame(anyString(), any(), anyString());
  }

  // ─── patch frame ─────────────────────────────────────────────────────────────

  @Test
  void patchFrame_returns200WithUpdatedFrame() {
    CoordinateFrame updated = frame(FRAME_APP_ID, "tool0_updated", null);
    updated.setX(0.1);
    when(sceneGraphService.patchFrame(eq(SCENE_APP_ID), eq(FRAME_APP_ID), any(), eq(CALLER)))
      .thenReturn(updated);

    PatchFrameIO patch = new PatchFrameIO("tool0_updated", null, 0.1, null, null, null, null, null);
    Response r = resource.patchFrame(SCENE_APP_ID, FRAME_APP_ID, patch, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    CoordinateFrameIO io = (CoordinateFrameIO) r.getEntity();
    assertThat(io.name()).isEqualTo("tool0_updated");
    assertThat(io.x()).isEqualTo(0.1);
  }

  @Test
  void patchFrame_returns404WhenFrameNotFound() {
    when(sceneGraphService.patchFrame(eq(SCENE_APP_ID), eq(FRAME_APP_ID), any(), eq(CALLER)))
      .thenReturn(null);

    assertThat(resource.patchFrame(SCENE_APP_ID, FRAME_APP_ID, new PatchFrameIO(null, null, null, null, null, null, null, null), sc)
      .getStatus()).isEqualTo(404);
  }

  // ─── delete frame ────────────────────────────────────────────────────────────

  @Test
  void deleteFrame_returns204OnSuccess() {
    when(sceneGraphService.deleteFrame(eq(SCENE_APP_ID), eq(FRAME_APP_ID), eq(CALLER)))
      .thenReturn(true);

    assertThat(resource.deleteFrame(SCENE_APP_ID, FRAME_APP_ID, sc).getStatus()).isEqualTo(204);
  }

  @Test
  void deleteFrame_returns404WhenFrameMissing() {
    when(sceneGraphService.deleteFrame(eq(SCENE_APP_ID), eq(FRAME_APP_ID), eq(CALLER)))
      .thenReturn(false);

    assertThat(resource.deleteFrame(SCENE_APP_ID, FRAME_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  // ─── add joint ───────────────────────────────────────────────────────────────

  @Test
  void addJoint_returns201WithJoint() {
    Joint joint = joint(JOINT_APP_ID, "joint_1");
    when(sceneGraphService.addJoint(eq(SCENE_APP_ID), any(), eq(CALLER))).thenReturn(joint);

    CreateJointIO body = new CreateJointIO("joint_1", FRAME_APP_ID, "frame-002",
      0, 0, 1, -3.14, 3.14, "REVOLUTE", 0.0);
    Response r = resource.addJoint(SCENE_APP_ID, body, sc);

    assertThat(r.getStatus()).isEqualTo(201);
    JointIO io = (JointIO) r.getEntity();
    assertThat(io.appId()).isEqualTo(JOINT_APP_ID);
    assertThat(io.name()).isEqualTo("joint_1");
    assertThat(io.type()).isEqualTo("REVOLUTE");
  }

  @Test
  void addJoint_returns404WhenSceneMissing() {
    when(sceneGraphService.addJoint(eq(SCENE_APP_ID), any(), eq(CALLER))).thenReturn(null);

    CreateJointIO body = new CreateJointIO("j", null, null, 0, 0, 1, 0, 0, "FIXED", 0);
    assertThat(resource.addJoint(SCENE_APP_ID, body, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void addJoint_returns400WhenBodyNull() {
    assertThat(resource.addJoint(SCENE_APP_ID, null, sc).getStatus()).isEqualTo(400);
    verify(sceneGraphService, never()).addJoint(anyString(), any(), anyString());
  }

  // ─── delete scene ────────────────────────────────────────────────────────────

  @Test
  void deleteScene_returns204OnSuccess() {
    when(sceneGraphService.deleteScene(eq(SCENE_APP_ID), eq(CALLER))).thenReturn(true);
    assertThat(resource.deleteScene(SCENE_APP_ID, sc).getStatus()).isEqualTo(204);
  }

  @Test
  void deleteScene_returns404WhenMissing() {
    when(sceneGraphService.deleteScene(eq(SCENE_APP_ID), eq(CALLER))).thenReturn(false);
    assertThat(resource.deleteScene(SCENE_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  // ─── 404 on unknown appId ─────────────────────────────────────────────────────

  @Test
  void getScene_returns404ForUnknownAppId() {
    when(sceneGraphService.getScene("unknown-999")).thenReturn(null);
    assertThat(resource.getScene("unknown-999", sc).getStatus()).isEqualTo(404);
  }

  // ─── entity builders ─────────────────────────────────────────────────────────

  private static DigitalTwinScene scene(String appId, String name) {
    DigitalTwinScene s = new DigitalTwinScene();
    s.setAppId(appId);
    s.setName(name);
    return s;
  }

  private static CoordinateFrame frame(String appId, String name, String parentAppId) {
    CoordinateFrame f = new CoordinateFrame();
    f.setAppId(appId);
    f.setName(name);
    f.setParentFrameAppId(parentAppId);
    f.setKind(FrameKind.FRAME);
    return f;
  }

  private static Joint joint(String appId, String name) {
    Joint j = new Joint();
    j.setAppId(appId);
    j.setName(name);
    j.setType(JointType.REVOLUTE);
    j.setAxisZ(1.0);
    j.setLimitMin(-Math.PI);
    j.setLimitMax(Math.PI);
    return j;
  }
}
