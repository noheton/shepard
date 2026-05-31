package de.dlr.shepard.v2.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.security.AuthenticationContext;
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
import de.dlr.shepard.v2.scenegraph.io.SceneGraphListItemIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.ProvenanceContext;
import de.dlr.shepard.v2.scenegraph.services.ScenegraphFromUrdfService;
import de.dlr.shepard.v2.scenegraph.services.ScenegraphFromUrdfService.ExistingSceneException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

  /**
   * MCP-COV-08 — wired for the {@code scene_create_from_urdf} tool so the
   * service-layer permission walk + URDF parse + scene+frames+joints mint
   * all live in one place and the MCP tool is a thin wrapper.
   */
  @Inject ScenegraphFromUrdfService scenegraphFromUrdfService;

  /**
   * MCP-COV-08 — current authenticated principal for the URDF mint
   * permission walk. Populated by {@link McpAuthFilter} before any tool
   * method runs.
   */
  @Inject AuthenticationContext authenticationContext;

  /**
   * MCP-COV-08 — used to read the {@code X-AI-Agent} header on the
   * {@code scene_create_from_urdf} tool so the mint Activity records
   * AI provenance per the f(ai)²r split.
   */
  @Inject Instance<CurrentVertxRequest> currentVertxRequest;

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

  // ── MCP-COV-08 — scene_list ──────────────────────────────────────────────

  /** Cap mirrors {@link de.dlr.shepard.v2.scenegraph.resources.SceneGraphRest#list}. */
  static final int SCENE_LIST_MAX_SIZE = 200;
  /** Default page size — matches the REST endpoint default. */
  static final int SCENE_LIST_DEFAULT_SIZE = 50;

  @Tool(
    name = "scene_list",
    description =
      "List `:DigitalTwinScene` rows in the instance, ordered by `updatedAt DESC` so " +
      "the most recently-touched scenes appear first (ties broken by `appId ASC`).\n\n" +
      "Mirrors `GET /v2/scene-graphs`. Returns an envelope with `items[]`, `total`, " +
      "`page`, `size`. Each item carries: `appId`, `name`, `description`, " +
      "`sourceFileAppId`, `rootFrameAppId`, `createdAt`, `updatedAt`, `frameCount`, " +
      "`jointCount`.\n\n" +
      "Pagination: omit `page` / `size` to get the first 50; the server caps `size` at " +
      SCENE_LIST_MAX_SIZE + " to avoid unbounded result sets.\n\n" +
      "Auth: any authenticated user (per the SCENEGRAPH-REST-1 posture; no per-scene " +
      "permission gate yet — see `SCENEGRAPH-PERMS-1`)."
  )
  public String sceneList(
    @ToolArg(required = false, description = "Zero-based page index. Default 0.") Integer page,
    @ToolArg(required = false, description = "Page size, clamped to [1, " + SCENE_LIST_MAX_SIZE + "]. Default " + SCENE_LIST_DEFAULT_SIZE + ".") Integer size
  ) {
    return support.run("scene_list", () -> {
      contextBridge.bind();
      int safePage = page == null ? 0 : Math.max(page, 0);
      int safeSize = size == null
        ? SCENE_LIST_DEFAULT_SIZE
        : Math.min(Math.max(size, 1), SCENE_LIST_MAX_SIZE);

      SceneGraphService.SceneListPage src = sceneGraphService.listScenes(safePage, safeSize);

      List<Map<String, Object>> items = new ArrayList<>(src.rows().size());
      for (SceneGraphService.SceneListRow r : src.rows()) {
        // Reuse the existing IO shape so the wire fields stay in lock-step
        // with the REST envelope — any future field added to the list-item
        // row flows here for free.
        items.add(toListItemMap(new SceneGraphListItemIO(r)));
      }

      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("items", items);
      envelope.put("total", src.total());
      envelope.put("page", safePage);
      envelope.put("size", safeSize);
      return support.toJson(envelope);
    });
  }

  // ── MCP-COV-08 — scene_create_from_urdf ──────────────────────────────────

  @Tool(
    name = "scene_create_from_urdf",
    description =
      "Mint a `:DigitalTwinScene` by parsing a URDF singleton FileReference in one " +
      "call. Mirrors `POST /v2/scene-graphs/from-urdf/{fileReferenceAppId}`.\n\n" +
      "Resolves the FileReference (FR1b singleton), streams its URDF XML content, " +
      "parses it, and materialises one `:CoordinateFrame` per `<link>` and one " +
      "`:Joint` per `<joint>` — all via the service layer so every mutation is " +
      "captured as a `:Activity`. A `urn:shepard:scenegraph:scene-appId` annotation " +
      "is stamped on the FileReference so subsequent calls can route to the existing " +
      "scene.\n\n" +
      "Idempotency: if the FileReference already carries a scene-appId back-" +
      "annotation, the tool returns the existing scene appId in `{existingSceneAppId}` " +
      "rather than erroring — so re-invocation is safe.\n\n" +
      "Auth: caller must have Write on the parent Collection of the FileReference " +
      "(inherited via the DataObject → Collection chain).\n\n" +
      "Errors: -32602 when the FileReference is missing / is a multi-file bundle / " +
      "the URDF body is invalid; -32002 when the caller lacks Write permission."
  )
  public String sceneCreateFromUrdf(
    @ToolArg(description = "UUID v7 of the singleton `:FileReference` carrying the URDF bytes.") String fileReferenceAppId,
    @ToolArg(required = false, description = "Optional scene name; defaults to the URDF `<robot name=...>` attribute or the FileReference name.") String name,
    @ToolArg(required = false, description = "Optional scene description.") String description
  ) {
    return support.run("scene_create_from_urdf", () -> {
      contextBridge.bind();

      String caller = authenticationContext == null ? null : authenticationContext.getCurrentUserName();
      if (caller == null || caller.isBlank()) {
        throw new jakarta.ws.rs.NotAuthorizedException(
          "Authentication required to mint a scene from a URDF FileReference."
        );
      }

      String aiAgent = readAiAgentHeader();
      ProvenanceContext prov = ProvenanceContext.from(caller, aiAgent);

      try {
        DigitalTwinScene scene = scenegraphFromUrdfService.createFromUrdf(
          fileReferenceAppId, name, description, prov, caller
        );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "created");
        out.put("sceneAppId", scene.getAppId());
        out.put("scene", new SceneGraphIO(scene, List.of(), List.of()));
        return support.toJson(out);
      } catch (ExistingSceneException ese) {
        // Idempotent 409 — return the existing scene appId so the caller
        // routes to it instead of erroring.
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "exists");
        out.put("existingSceneAppId", ese.getExistingSceneAppId());
        out.put("fileReferenceAppId", fileReferenceAppId);
        return support.toJson(out);
      } catch (ForbiddenException fe) {
        // Map to permission-denied at the MCP layer so the agent gets a
        // clean -32002 (Permission denied) instead of the wrapped 403.
        throw fe;
      } catch (NotFoundException nfe) {
        // McpToolSupport.run turns NotFoundException → -32602 invalid params
        // (with the original message) — exactly the shape an agent can
        // self-correct from.
        throw nfe;
      } catch (BadRequestException bre) {
        // BadRequestException extends WebApplicationException (a runtime
        // exception); McpToolSupport.run would otherwise wrap it as
        // INTERNAL_ERROR. Re-raise as INVALID_PARAMS with the original
        // message so the agent sees the caller-fixable shape.
        throw McpToolSupport.invalidParams(
          bre.getMessage() == null ? "Bad request" : bre.getMessage()
        );
      }
    });
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /**
   * Read the {@code X-AI-Agent} header from the current Vert.x routing
   * context (used by {@link #sceneCreateFromUrdf} to mark the mint
   * Activity's source mode as {@code ai}). Returns {@code null} when
   * unset or when no routing context is available — matches the
   * fall-back used by {@link AnnotationMcpTools#isAiAgentRequest()}.
   */
  private String readAiAgentHeader() {
    try {
      if (currentVertxRequest == null) return null;
      CurrentVertxRequest cvr = currentVertxRequest.get();
      RoutingContext rc = cvr == null ? null : cvr.getCurrent();
      if (rc == null) return null;
      String h = rc.request().getHeader(McpToolSupport.HEADER_AI_AGENT);
      return (h == null || h.isBlank()) ? null : h;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * Convert a {@link SceneGraphListItemIO} to a plain map so the
   * {@code scene_list} envelope serialises through the {@link McpToolSupport#toJson}
   * path without needing the IO class on the wire.
   */
  private static Map<String, Object> toListItemMap(SceneGraphListItemIO item) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("appId", item.getAppId());
    row.put("name", item.getName());
    row.put("description", item.getDescription());
    row.put("sourceFileAppId", item.getSourceFileAppId());
    row.put("rootFrameAppId", item.getRootFrameAppId());
    row.put("createdAt", item.getCreatedAt());
    row.put("updatedAt", item.getUpdatedAt());
    row.put("frameCount", item.getFrameCount());
    row.put("jointCount", item.getJointCount());
    return row;
  }

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
