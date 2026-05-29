package de.dlr.shepard.v2.scenegraph.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.io.CoordinateFrameIO;
import de.dlr.shepard.v2.scenegraph.io.CreateFrameIO;
import de.dlr.shepard.v2.scenegraph.io.CreateJointIO;
import de.dlr.shepard.v2.scenegraph.io.CreateSceneIO;
import de.dlr.shepard.v2.scenegraph.io.DigitalTwinSceneIO;
import de.dlr.shepard.v2.scenegraph.io.JointIO;
import de.dlr.shepard.v2.scenegraph.io.PatchFrameIO;
import de.dlr.shepard.v2.scenegraph.io.SceneGraphIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SCENEGRAPH-REST-1 — REST surface for {@link DigitalTwinScene} /
 * {@link CoordinateFrame} / {@link Joint} CRUD.
 *
 * <p>All endpoints live under {@code /v2/scene-graphs} (fork-only
 * development surface; no impact on the frozen
 * {@code /shepard/api/...} v1 surface — per CLAUDE.md API-version
 * policy).
 *
 * <p>Auth: {@code @Authenticated} on the class — any authenticated
 * user can read and write scenes. Per-scene ownership checks tied to
 * a parent DataObject are deferred to {@code SCENEGRAPH-PERM-1}.
 * The first consumer (RDK-PARSE-2 parser) needs no per-entity gate.
 * When scenes are attached to DataObjects in SCENEGRAPH-PERM-1, the
 * permission model will mirror the DataObject's parent Collection
 * permissions.
 *
 * <p>Provenance: every mutating handler calls
 * {@link de.dlr.shepard.v2.scenegraph.services.SceneGraphService}
 * which records a {@code :Activity} via
 * {@link de.dlr.shepard.provenance.services.ProvenanceService}. The
 * handler then sets {@link ProvenanceCaptureFilter#PROP_SKIP_CAPTURE}
 * so the response filter does not write a duplicate row — per the
 * "handlers that record their own Activity hand off skip-capture"
 * rule in CLAUDE.md.
 *
 * @see SceneGraphService
 */
@Path("/v2/scene-graphs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Scene graphs (SCENEGRAPH-REST-1)")
public class SceneGraphRest {

  private static final String PROBLEM_NOT_FOUND = "/problems/scene-graph.not-found";
  private static final String PROBLEM_BAD_REQUEST = "/problems/scene-graph.bad-request";

  @Inject
  SceneGraphService sceneGraphService;

  @Context
  ContainerRequestContext requestContext;

  // ─── Scene CRUD ─────────────────────────────────────────────────────────────

  /**
   * Create a new {@link DigitalTwinScene}.
   */
  @POST
  @Operation(
    summary = "Create a digital-twin scene.",
    description =
      "Creates a new `:DigitalTwinScene` node. All fields are optional — the server " +
      "always mints a UUID v7 `appId`. Returns `201 Created` with the full " +
      "`DigitalTwinSceneIO` body.\n\n" +
      "Next step: `POST /v2/scene-graphs/{appId}/frames` to add coordinate frames, " +
      "`POST /v2/scene-graphs/{appId}/joints` to add joints."
  )
  @APIResponse(
    responseCode = "201",
    description = "Scene created.",
    content = @Content(schema = @Schema(implementation = DigitalTwinSceneIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response createScene(CreateSceneIO body, @Context SecurityContext sc) {
    String caller = callerName(sc);
    if (caller == null) return unauthorized();

    DigitalTwinScene scene = sceneGraphService.createScene(body, caller);
    skipCapture();
    return Response.status(Response.Status.CREATED)
      .entity(new DigitalTwinSceneIO(scene))
      .build();
  }

  /**
   * Get a full scene graph: scene header + all frames + all joints.
   */
  @GET
  @Path("/{appId}")
  @Operation(
    summary = "Get a scene graph (header + all frames + all joints).",
    description =
      "Returns the full scene graph payload: the `:DigitalTwinScene` header in " +
      "`scene`, all `:CoordinateFrame` nodes in `frames`, and all `:Joint` nodes " +
      "in `joints`. Frames and joints are returned as flat lists — clients assemble " +
      "the tree using `parentFrameAppId` on each frame.\n\n" +
      "Returns `404` when no scene with that `appId` exists."
  )
  @APIResponse(
    responseCode = "200",
    description = "Scene graph payload.",
    content = @Content(schema = @Schema(implementation = SceneGraphIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Scene not found.")
  public Response getScene(@PathParam("appId") String appId, @Context SecurityContext sc) {
    if (callerName(sc) == null) return unauthorized();

    SceneGraphIO graph = sceneGraphService.getScene(appId);
    if (graph == null) return notFound("DigitalTwinScene", appId);

    return Response.ok(graph).build();
  }

  /**
   * Delete a scene and all its frames and joints.
   */
  @DELETE
  @Path("/{appId}")
  @Operation(
    summary = "Delete a scene graph (and all its frames + joints).",
    description =
      "Deletes the `:DigitalTwinScene` node and all reachable `:CoordinateFrame` " +
      "and `:Joint` nodes. This is a hard delete — all Neo4j nodes and their " +
      "relationships are removed. Returns `204 No Content` on success or `404` " +
      "when the scene does not exist."
  )
  @APIResponse(responseCode = "204", description = "Scene deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Scene not found.")
  public Response deleteScene(@PathParam("appId") String appId, @Context SecurityContext sc) {
    String caller = callerName(sc);
    if (caller == null) return unauthorized();

    boolean deleted = sceneGraphService.deleteScene(appId, caller);
    if (!deleted) return notFound("DigitalTwinScene", appId);

    skipCapture();
    return Response.noContent().build();
  }

  // ─── Frame CRUD ──────────────────────────────────────────────────────────────

  /**
   * Add a coordinate frame to a scene.
   */
  @POST
  @Path("/{appId}/frames")
  @Operation(
    summary = "Add a coordinate frame to a scene.",
    description =
      "Creates a `:CoordinateFrame` node and attaches it to the scene via " +
      "`[:HAS_FRAME]`. If `parentFrameAppId` is non-null, also writes " +
      "`[:HAS_PARENT_FRAME]` to the parent. Translation/rotation fields default " +
      "to `0.0` (identity transform) when omitted. `kind` defaults to `FRAME` " +
      "when omitted or unknown.\n\n" +
      "Returns `201 Created` with the new `CoordinateFrameIO`. Returns `404` when " +
      "the parent scene does not exist."
  )
  @APIResponse(
    responseCode = "201",
    description = "Frame added.",
    content = @Content(schema = @Schema(implementation = CoordinateFrameIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing required frame body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Scene not found.")
  public Response addFrame(
    @PathParam("appId") String sceneAppId,
    CreateFrameIO body,
    @Context SecurityContext sc
  ) {
    String caller = callerName(sc);
    if (caller == null) return unauthorized();
    if (body == null) {
      return problem(PROBLEM_BAD_REQUEST, "Missing request body",
        Response.Status.BAD_REQUEST, "CreateFrameIO body is required.");
    }

    CoordinateFrame frame = sceneGraphService.addFrame(sceneAppId, body, caller);
    if (frame == null) return notFound("DigitalTwinScene", sceneAppId);

    skipCapture();
    return Response.status(Response.Status.CREATED)
      .entity(new CoordinateFrameIO(frame))
      .build();
  }

  /**
   * RFC 7396 merge-patch a coordinate frame.
   */
  @PATCH
  @Path("/{appId}/frames/{frameAppId}")
  @Consumes("application/merge-patch+json")
  @Operation(
    summary = "Merge-patch a coordinate frame (RFC 7396).",
    description =
      "Applies a partial update to an existing `:CoordinateFrame`. Only non-null " +
      "fields in the request body are applied; missing fields keep their current " +
      "value. If `parentFrameAppId` changes (including to `\"\"` to clear it), " +
      "the old `[:HAS_PARENT_FRAME]` edge is removed and a new one written.\n\n" +
      "Content-Type must be `application/merge-patch+json`. Returns `200 OK` with " +
      "the updated `CoordinateFrameIO`. Returns `404` when the scene or frame " +
      "does not exist (or the frame does not belong to that scene)."
  )
  @APIResponse(
    responseCode = "200",
    description = "Frame updated.",
    content = @Content(schema = @Schema(implementation = CoordinateFrameIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Scene or frame not found.")
  public Response patchFrame(
    @PathParam("appId") String sceneAppId,
    @PathParam("frameAppId") String frameAppId,
    PatchFrameIO body,
    @Context SecurityContext sc
  ) {
    String caller = callerName(sc);
    if (caller == null) return unauthorized();

    CoordinateFrame frame = sceneGraphService.patchFrame(sceneAppId, frameAppId, body, caller);
    if (frame == null) {
      return notFound("CoordinateFrame", frameAppId + " in scene " + sceneAppId);
    }

    skipCapture();
    return Response.ok(new CoordinateFrameIO(frame)).build();
  }

  /**
   * Delete a coordinate frame from a scene.
   */
  @DELETE
  @Path("/{appId}/frames/{frameAppId}")
  @Operation(
    summary = "Delete a coordinate frame.",
    description =
      "Deletes a `:CoordinateFrame` node and all its relationships. Does NOT " +
      "recursively delete child frames — callers must delete leaf frames first, " +
      "or use `DELETE /v2/scene-graphs/{appId}` which bulk-deletes the whole tree.\n\n" +
      "Returns `204 No Content` on success, `404` when the frame does not exist " +
      "or does not belong to the given scene."
  )
  @APIResponse(responseCode = "204", description = "Frame deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Frame not found in this scene.")
  public Response deleteFrame(
    @PathParam("appId") String sceneAppId,
    @PathParam("frameAppId") String frameAppId,
    @Context SecurityContext sc
  ) {
    String caller = callerName(sc);
    if (caller == null) return unauthorized();

    boolean deleted = sceneGraphService.deleteFrame(sceneAppId, frameAppId, caller);
    if (!deleted) {
      return notFound("CoordinateFrame", frameAppId + " in scene " + sceneAppId);
    }

    skipCapture();
    return Response.noContent().build();
  }

  // ─── Joint CRUD ──────────────────────────────────────────────────────────────

  /**
   * Add a kinematic joint to a scene.
   */
  @POST
  @Path("/{appId}/joints")
  @Operation(
    summary = "Add a joint to a scene.",
    description =
      "Creates a `:Joint` node and attaches it to the scene via `[:HAS_JOINT]`. " +
      "Also writes `[:JOINT_PARENT]` and `[:JOINT_CHILD]` edges to the parent and " +
      "child `:CoordinateFrame` nodes (when `parentFrameAppId` / `childFrameAppId` " +
      "are non-null). `type` defaults to `FIXED` when omitted or unknown.\n\n" +
      "Returns `201 Created` with the new `JointIO`. Returns `404` when the " +
      "parent scene does not exist."
  )
  @APIResponse(
    responseCode = "201",
    description = "Joint added.",
    content = @Content(schema = @Schema(implementation = JointIO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing required joint body.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Scene not found.")
  public Response addJoint(
    @PathParam("appId") String sceneAppId,
    CreateJointIO body,
    @Context SecurityContext sc
  ) {
    String caller = callerName(sc);
    if (caller == null) return unauthorized();
    if (body == null) {
      return problem(PROBLEM_BAD_REQUEST, "Missing request body",
        Response.Status.BAD_REQUEST, "CreateJointIO body is required.");
    }

    Joint joint = sceneGraphService.addJoint(sceneAppId, body, caller);
    if (joint == null) return notFound("DigitalTwinScene", sceneAppId);

    skipCapture();
    return Response.status(Response.Status.CREATED)
      .entity(new JointIO(joint))
      .build();
  }

  // ─── helpers ─────────────────────────────────────────────────────────────────

  private static String callerName(SecurityContext sc) {
    if (sc == null || sc.getUserPrincipal() == null) return null;
    String n = sc.getUserPrincipal().getName();
    return (n == null || n.isBlank()) ? null : n;
  }

  private static Response unauthorized() {
    return Response.status(Response.Status.UNAUTHORIZED).build();
  }

  private static Response notFound(String kind, String id) {
    return problem(PROBLEM_NOT_FOUND, "Not found",
      Response.Status.NOT_FOUND, kind + " with id '" + id + "' not found.");
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status)
      .type("application/problem+json")
      .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null))
      .build();
  }

  /**
   * Signal the {@link ProvenanceCaptureFilter} not to write a second
   * duplicate {@code :Activity} row. The service layer has already
   * recorded the Activity via {@link de.dlr.shepard.provenance.services.ProvenanceService}.
   * Per "handlers that record their own Activity hand off skip-capture"
   * in CLAUDE.md.
   */
  private void skipCapture() {
    try {
      if (requestContext != null) {
        requestContext.setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
      }
    } catch (RuntimeException ignored) { /* best-effort */ }
  }
}
