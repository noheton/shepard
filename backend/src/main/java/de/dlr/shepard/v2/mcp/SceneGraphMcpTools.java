package de.dlr.shepard.v2.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.entities.JointType;
import de.dlr.shepard.v2.scenegraph.export.UrdfExporter;
import de.dlr.shepard.v2.scenegraph.io.CreateFrameRequestIO;
import de.dlr.shepard.v2.scenegraph.io.CreateJointRequestIO;
import de.dlr.shepard.v2.scenegraph.io.CreateSceneRequestIO;
import de.dlr.shepard.v2.scenegraph.io.FrameIO;
import de.dlr.shepard.v2.scenegraph.io.JointIO;
import de.dlr.shepard.v2.scenegraph.io.PatchFrameRequestIO;
import de.dlr.shepard.v2.scenegraph.io.SceneGraphIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.ProvenanceContext;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * SCENEGRAPH-REST-1 — MCP tool surface for scene-graph CRUD.
 *
 * <p>Mirrors the REST surface so an AI agent can drive scene edits with
 * full provenance — every tool call routes through
 * {@link SceneGraphService}, which is where the {@code :Activity} +
 * PROV-O + {@code :WAS_DERIVED_FROM} writes happen. The
 * {@link McpToolSupport#run} wrapper also captures an inbound TPL9
 * AI Activity when the call carries an {@code X-AI-Agent} header, so
 * the audit trail records both who drove the call (PROV1j) and the
 * MCP tool invocation itself.
 *
 * <p>Auth is enforced upstream by {@link McpAuthFilter}; tool methods
 * may assume an authenticated caller.
 */
@ApplicationScoped
public class SceneGraphMcpTools {

  @Inject SceneGraphService sceneGraphService;
  @Inject UrdfExporter urdfExporter;
  @Inject McpContextBridge contextBridge;
  @Inject McpToolSupport support;
  @Inject ObjectMapper objectMapper;

  @Tool(
    name = "scene_graph_get",
    description =
      "Load a digital-twin scene by appId with its full frame tree and joints. " +
      "Use this to enumerate the kinematic structure of a parsed robot model " +
      "(URDF / .rdk import target). Returns the SceneGraphIO JSON shape: " +
      "scene metadata + frames[] + joints[].\n\n" +
      "Pair with `scene_graph_export_urdf` when you need the URDF XML form for " +
      "an external visualiser (Foxglove, RViz, Isaac)."
  )
  public String sceneGraphGet(
    @ToolArg(description = "UUID v7 appId of the DigitalTwinScene.") String appId
  ) {
    return support.run("scene_graph_get", () -> {
      contextBridge.bind();
      DigitalTwinScene scene = sceneGraphService.findScene(appId);
      if (scene == null) {
        throw McpToolSupport.invalidParams("No scene found for appId=" + appId);
      }
      SceneGraphIO io = new SceneGraphIO(
        scene,
        toFrameIOs(sceneGraphService.findFramesForScene(appId)),
        toJointIOs(sceneGraphService.findJointsForScene(appId))
      );
      return support.toJson(io);
    });
  }

  @Tool(
    name = "scene_graph_add_frame",
    description =
      "Add a CoordinateFrame to a scene. If the scene is empty, the first frame " +
      "added becomes the root. Pass an existing frame's appId as " +
      "`parentFrameAppId` to attach beneath it. The six scalars (x/y/z metres, " +
      "rx/ry/rz radians Euler) form the frame's local transform relative to " +
      "the parent.\n\n" +
      "Records an UPDATE :Activity on the scene with the new frame in the " +
      "PROV-O `:GENERATED`-side."
  )
  public String sceneGraphAddFrame(
    @ToolArg(description = "UUID v7 appId of the scene.") String sceneAppId,
    @ToolArg(required = false, description = "Frame name (e.g. 'tool0').") String name,
    @ToolArg(required = false, description = "Parent frame appId; omit for the first frame.") String parentFrameAppId,
    @ToolArg(required = false, description = "Translation x (m).") Double x,
    @ToolArg(required = false, description = "Translation y (m).") Double y,
    @ToolArg(required = false, description = "Translation z (m).") Double z,
    @ToolArg(required = false, description = "Roll rx (rad).") Double rx,
    @ToolArg(required = false, description = "Pitch ry (rad).") Double ry,
    @ToolArg(required = false, description = "Yaw rz (rad).") Double rz,
    @ToolArg(required = false, description = "FrameKind: FRAME, JOINT, TOOL, BASE, TCP.") String kind
  ) {
    return support.run("scene_graph_add_frame", () -> {
      contextBridge.bind();
      CreateFrameRequestIO body = new CreateFrameRequestIO();
      body.setName(name);
      body.setParentFrameAppId(parentFrameAppId);
      body.setX(x); body.setY(y); body.setZ(z);
      body.setRx(rx); body.setRy(ry); body.setRz(rz);
      if (kind != null && !kind.isBlank()) {
        try { body.setKind(FrameKind.valueOf(kind.toUpperCase())); }
        catch (IllegalArgumentException e) {
          throw McpToolSupport.invalidParams("Unknown FrameKind: " + kind);
        }
      }
      CoordinateFrame f = sceneGraphService.addFrame(sceneAppId, body, ProvenanceContext.from(null, null));
      return support.toJson(new FrameIO(f));
    });
  }

  @Tool(
    name = "scene_graph_patch_frame",
    description =
      "Mutate a single frame's transform fields, kind, name, or parent pointer. " +
      "Pass empty string for `parentFrameAppId` to make the frame a root. " +
      "Fields not supplied are left unchanged."
  )
  public String sceneGraphPatchFrame(
    @ToolArg(description = "UUID v7 appId of the scene.") String sceneAppId,
    @ToolArg(description = "UUID v7 appId of the frame to mutate.") String frameAppId,
    @ToolArg(required = false, description = "New name.") String name,
    @ToolArg(required = false, description = "New parent appId; empty string clears parent (=> root).") String parentFrameAppId,
    @ToolArg(required = false, description = "Translation x (m).") Double x,
    @ToolArg(required = false, description = "Translation y (m).") Double y,
    @ToolArg(required = false, description = "Translation z (m).") Double z,
    @ToolArg(required = false, description = "Roll rx (rad).") Double rx,
    @ToolArg(required = false, description = "Pitch ry (rad).") Double ry,
    @ToolArg(required = false, description = "Yaw rz (rad).") Double rz,
    @ToolArg(required = false, description = "FrameKind: FRAME, JOINT, TOOL, BASE, TCP.") String kind
  ) {
    return support.run("scene_graph_patch_frame", () -> {
      contextBridge.bind();
      PatchFrameRequestIO body = new PatchFrameRequestIO();
      body.setName(name);
      body.setParentFrameAppId(parentFrameAppId);
      body.setX(x); body.setY(y); body.setZ(z);
      body.setRx(rx); body.setRy(ry); body.setRz(rz);
      if (kind != null && !kind.isBlank()) {
        try { body.setKind(FrameKind.valueOf(kind.toUpperCase())); }
        catch (IllegalArgumentException e) {
          throw McpToolSupport.invalidParams("Unknown FrameKind: " + kind);
        }
      }
      CoordinateFrame f = sceneGraphService.patchFrame(sceneAppId, frameAppId, body, ProvenanceContext.from(null, null));
      return support.toJson(new FrameIO(f));
    });
  }

  @Tool(
    name = "scene_graph_delete_frame",
    description =
      "Hard-delete a frame and every descendant via the `:HAS_PARENT_FRAME` " +
      "chain. Any joint touching a deleted frame is also removed. Records a " +
      "DELETE :Activity on the scene."
  )
  public String sceneGraphDeleteFrame(
    @ToolArg(description = "UUID v7 appId of the scene.") String sceneAppId,
    @ToolArg(description = "UUID v7 appId of the frame (root of the subtree to delete).") String frameAppId
  ) {
    return support.run("scene_graph_delete_frame", () -> {
      contextBridge.bind();
      sceneGraphService.deleteFrameSubtree(sceneAppId, frameAppId, ProvenanceContext.from(null, null));
      return "{\"deleted\":true,\"frameAppId\":\"" + frameAppId + "\"}";
    });
  }

  @Tool(
    name = "scene_graph_register_joint",
    description =
      "Register a kinematic joint between two existing frames in a scene. " +
      "JointType: REVOLUTE (rotational with limits), PRISMATIC (translational " +
      "with limits), CONTINUOUS (rotational no limits), FIXED (rigid). The " +
      "axis triple defines the joint's axis of motion in the parent frame's " +
      "coordinate system."
  )
  public String sceneGraphRegisterJoint(
    @ToolArg(description = "UUID v7 appId of the scene.") String sceneAppId,
    @ToolArg(description = "UUID v7 appId of the parent (proximal) frame.") String parentFrameAppId,
    @ToolArg(description = "UUID v7 appId of the child (distal) frame.") String childFrameAppId,
    @ToolArg(required = false, description = "Joint label.") String name,
    @ToolArg(required = false, description = "Axis x.") Double axisX,
    @ToolArg(required = false, description = "Axis y.") Double axisY,
    @ToolArg(required = false, description = "Axis z.") Double axisZ,
    @ToolArg(required = false, description = "Min joint position.") Double limitMin,
    @ToolArg(required = false, description = "Max joint position.") Double limitMax,
    @ToolArg(required = false, description = "JointType: REVOLUTE, PRISMATIC, FIXED, CONTINUOUS.") String type,
    @ToolArg(required = false, description = "Home position.") Double homeAngle
  ) {
    return support.run("scene_graph_register_joint", () -> {
      contextBridge.bind();
      CreateJointRequestIO body = new CreateJointRequestIO();
      body.setParentFrameAppId(parentFrameAppId);
      body.setChildFrameAppId(childFrameAppId);
      body.setName(name);
      body.setAxisX(axisX); body.setAxisY(axisY); body.setAxisZ(axisZ);
      body.setLimitMin(limitMin); body.setLimitMax(limitMax);
      body.setHomeAngle(homeAngle);
      if (type != null && !type.isBlank()) {
        try { body.setType(JointType.valueOf(type.toUpperCase())); }
        catch (IllegalArgumentException e) {
          throw McpToolSupport.invalidParams("Unknown JointType: " + type);
        }
      }
      Joint j = sceneGraphService.addJoint(sceneAppId, body, ProvenanceContext.from(null, null));
      return support.toJson(new JointIO(j));
    });
  }

  @Tool(
    name = "scene_graph_delete_joint",
    description = "Remove a joint from a scene. Records a DELETE :Activity."
  )
  public String sceneGraphDeleteJoint(
    @ToolArg(description = "UUID v7 appId of the scene.") String sceneAppId,
    @ToolArg(description = "UUID v7 appId of the joint to delete.") String jointAppId
  ) {
    return support.run("scene_graph_delete_joint", () -> {
      contextBridge.bind();
      sceneGraphService.deleteJoint(sceneAppId, jointAppId, ProvenanceContext.from(null, null));
      return "{\"deleted\":true,\"jointAppId\":\"" + jointAppId + "\"}";
    });
  }

  @Tool(
    name = "scene_graph_export_urdf",
    description =
      "Export a scene as URDF XML. URDF is the canonical robot-description " +
      "format consumed by RViz, Foxglove, Isaac Sim, MuJoCo, Gazebo, etc. " +
      "The export emits `<link>` per CoordinateFrame and `<joint>` per Joint " +
      "with axis + limits; visual / collision / inertial blocks are NOT " +
      "emitted (see UrdfExporter Javadoc for the deferred list).\n\n" +
      "Returns the URDF XML as a string (no XML escaping by this tool — " +
      "downstream consumers can pipe it directly to a file or a viewer)."
  )
  public String sceneGraphExportUrdf(
    @ToolArg(description = "UUID v7 appId of the scene.") String sceneAppId
  ) {
    return support.run("scene_graph_export_urdf", () -> {
      contextBridge.bind();
      DigitalTwinScene scene = sceneGraphService.findScene(sceneAppId);
      if (scene == null) {
        throw McpToolSupport.invalidParams("No scene found for appId=" + sceneAppId);
      }
      return urdfExporter.export(
        scene,
        sceneGraphService.findFramesForScene(sceneAppId),
        sceneGraphService.findJointsForScene(sceneAppId)
      );
    });
  }

  /**
   * Convenience entry point not exposed as a tool — but kept so the
   * v2 MCP test harness can drive create-scene through a stable path
   * without going through REST. AI Agents typically need a scene to
   * exist before issuing edits; the AI workflow is parser→createScene→
   * addFrame*→addJoint* with REST as the bootstrap.
   */
  @Tool(
    name = "scene_graph_create",
    description =
      "Create a new empty DigitalTwinScene. Returns the SceneGraphIO with the " +
      "minted appId so subsequent tool calls can drive add-frame / register-" +
      "joint. Optional name / description / sourceFileAppId set scene metadata " +
      "in the same call."
  )
  public String sceneGraphCreate(
    @ToolArg(required = false, description = "Scene name.") String name,
    @ToolArg(required = false, description = "Scene description.") String description,
    @ToolArg(required = false, description = "appId of source file (.rdk, .urdf), if any.") String sourceFileAppId
  ) {
    return support.run("scene_graph_create", () -> {
      contextBridge.bind();
      CreateSceneRequestIO body = new CreateSceneRequestIO();
      body.setName(name);
      body.setDescription(description);
      body.setSourceFileAppId(sourceFileAppId);
      DigitalTwinScene scene = sceneGraphService.createScene(body, ProvenanceContext.from(null, null));
      return support.toJson(new SceneGraphIO(scene, List.of(), List.of()));
    });
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static List<FrameIO> toFrameIOs(List<CoordinateFrame> frames) {
    List<FrameIO> out = new ArrayList<>(frames.size());
    for (CoordinateFrame f : frames) out.add(new FrameIO(f));
    return out;
  }

  private static List<JointIO> toJointIOs(List<Joint> joints) {
    List<JointIO> out = new ArrayList<>(joints.size());
    for (Joint j : joints) out.add(new JointIO(j));
    return out;
  }
}
