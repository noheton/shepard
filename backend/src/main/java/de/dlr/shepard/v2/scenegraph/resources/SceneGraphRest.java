package de.dlr.shepard.v2.scenegraph.resources;

import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.export.UrdfExporter;
import de.dlr.shepard.v2.scenegraph.io.CreateFrameRequestIO;
import de.dlr.shepard.v2.scenegraph.io.CreateJointRequestIO;
import de.dlr.shepard.v2.scenegraph.io.CreateSceneRequestIO;
import de.dlr.shepard.v2.scenegraph.io.FrameIO;
import de.dlr.shepard.v2.scenegraph.io.JointIO;
import de.dlr.shepard.v2.scenegraph.io.PatchFrameRequestIO;
import de.dlr.shepard.v2.scenegraph.io.SceneGraphIO;
import de.dlr.shepard.v2.scenegraph.io.SceneGraphListIO;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphPermissionService;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.ProvenanceContext;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SCENEGRAPH-REST-1 — REST surface for {@code :DigitalTwinScene}.
 *
 * <h2>Endpoint set</h2>
 * <pre>
 *   GET    /v2/scene-graphs                               — paginated list (SCENEGRAPH-LIST-1)
 *   GET    /v2/scene-graphs/{appId}                       — full scene tree
 *                                                            (Accept: application/ld+json for JSON-LD)
 *   GET    /v2/scene-graphs/{appId}/export.urdf           — URDF XML export
 *   GET    /v2/scene-graphs/{appId}/export.usd            — 503 (USD stub, ISAAC-USD-EXPORT-1)
 *   POST   /v2/scene-graphs                               — create empty scene
 *   POST   /v2/scene-graphs/{appId}/frames                — add frame
 *   PATCH  /v2/scene-graphs/{appId}/frames/{frameAppId}   — mutate frame transform + parent
 *   DELETE /v2/scene-graphs/{appId}/frames/{frameAppId}   — remove frame subtree
 *   POST   /v2/scene-graphs/{appId}/joints                — register joint
 *   DELETE /v2/scene-graphs/{appId}/joints/{jointAppId}   — remove joint
 * </pre>
 *
 * <h2>Auth (SCENEGRAPH-PERMS-1, shipped 2026-05-31)</h2>
 * <p>Per-scene permission walk via {@link SceneGraphPermissionService}: scene
 * inherits from {@code sourceFileAppId → FileReference → DataObject →
 * Collection}; read endpoints require {@link AccessType#Read} on the parent
 * Collection, write endpoints require {@link AccessType#Write}. The list
 * endpoint post-filters the page to the subset the caller can read (page
 * may return fewer rows than {@code size}; the {@code total} envelope field
 * still reports the unfiltered total — operators looking at the count vs.
 * row-count discrepancy can infer "you can't see N of these"). Hand-built
 * scenes (no {@code sourceFileAppId}) fail closed unless the caller carries
 * the {@code instance-admin} role; the eventual {@code ownerCollectionAppId}
 * field captured in the SCENEGRAPH-PERMS-1 backlog row is the planned shape
 * for first-class hand-built-scene anchoring.
 *
 * <h2>Provenance</h2>
 * <p>Every mutation records a {@code :Activity} via the
 * {@link SceneGraphService#recordActivity} path which wires the standard
 * PROV-O edges plus a supplementary {@code :WAS_DERIVED_FROM} edge to
 * the prior activity for the same scene. The handler reads
 * {@code X-AI-Agent} to populate {@code sourceMode} / {@code agentId}
 * on the activity (PROV1j) — closing the EU AI Act Art.50 disclosure at
 * the audit-log layer when an AI drives the call. Per the
 * "handlers that record their own Activity hand off skip-capture" rule,
 * every mutation sets {@link ProvenanceCaptureFilter#PROP_SKIP_CAPTURE}
 * after the service-layer record() call so the response filter does not
 * emit a duplicate row.
 */
@Path("/v2/scene-graphs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Scene graphs (v2)")
public class SceneGraphRest {

  static final String MEDIA_TYPE_JSON_LD = "application/ld+json";
  static final String MEDIA_TYPE_URDF = "application/xml";
  static final String HEADER_AI_AGENT = "X-AI-Agent";

  @Inject SceneGraphService sceneGraphService;
  @Inject UrdfExporter urdfExporter;
  @Inject SceneGraphPermissionService scenePermissions;

  @Context jakarta.ws.rs.container.ContainerRequestContext requestContext;

  // ── GET list ──────────────────────────────────────────────────────────────

  @GET
  @Operation(
    summary = "List scene graphs (paginated).",
    description =
      "Returns a page of `:DigitalTwinScene` rows with per-row frame and joint " +
      "counts. The page is ordered by `updatedAt DESC, appId ASC` so the most " +
      "recently-touched scenes appear first; ties break deterministically.\n\n" +
      "Pagination: omit `page` / `size` to get the first 50; the server caps " +
      "`size` at 200 to avoid unbounded result sets. The response envelope " +
      "carries `items[]`, `total`, `page`, and `size`.\n\n" +
      "Auth: any authenticated user. There is no per-scene permission gate " +
      "yet (see `SCENEGRAPH-PERMS-1` in the class Javadoc); every authenticated " +
      "caller sees the complete scene catalogue."
  )
  @APIResponse(
    responseCode = "200",
    description = "Page of scene graphs (may be empty).",
    content = @Content(schema = @Schema(implementation = SceneGraphListIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response list(
    @QueryParam("page") @DefaultValue("0") @PositiveOrZero int page,
    @QueryParam("size") @DefaultValue("50") @PositiveOrZero int size,
    @Context SecurityContext sc
  ) {
    String caller = callerOf(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    boolean isAdmin = sc != null && sc.isUserInRole(SceneGraphPermissionService.INSTANCE_ADMIN_ROLE);

    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);
    SceneGraphService.SceneListPage src = sceneGraphService.listScenes(safePage, safeSize);

    // SCENEGRAPH-PERMS-1 — post-filter the page to the subset the caller can
    // read. Page may return fewer rows than `size`; `total` envelope still
    // reports the unfiltered total per the class Javadoc.
    java.util.List<SceneGraphService.SceneListRow> filtered = new java.util.ArrayList<>(src.rows().size());
    for (SceneGraphService.SceneListRow row : src.rows()) {
      if (scenePermissions.isAllowed(row.appId(), AccessType.Read, caller, isAdmin)) {
        filtered.add(row);
      }
    }
    SceneGraphService.SceneListPage scoped = new SceneGraphService.SceneListPage(filtered, src.total());
    return Response.ok(new SceneGraphListIO(scoped, safePage, safeSize)).build();
  }

  // ── GET scene ─────────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}")
  @Produces({ MediaType.APPLICATION_JSON, MEDIA_TYPE_JSON_LD })
  @Operation(
    summary = "Get a scene graph by appId (full frame tree + joints).",
    description =
      "Returns the `:DigitalTwinScene` identified by `appId` (UUID v7) plus all " +
      "its frames and joints. " +
      "Set `Accept: application/ld+json` to receive a JSON-LD-framed response " +
      "(adds `@context` + `@type`); otherwise the body is plain JSON.\n\n" +
      "Auth: any authenticated user."
  )
  @APIResponse(
    responseCode = "200",
    description = "Scene found.",
    content = @Content(schema = @Schema(implementation = SceneGraphIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No scene with that appId.")
  public Response get(
    @PathParam("appId") @NotBlank String appId,
    @HeaderParam(HttpHeaders.ACCEPT) String accept,
    @Context SecurityContext sc
  ) {
    DigitalTwinScene scene = sceneGraphService.findScene(appId);
    if (scene == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkScenePermission(appId, AccessType.Read, sc);
    if (gate != null) return gate;

    List<CoordinateFrame> frames = sceneGraphService.findFramesForScene(appId);
    List<Joint> joints = sceneGraphService.findJointsForScene(appId);

    SceneGraphIO body = new SceneGraphIO(scene, toFrameIOs(frames), toJointIOs(joints));
    boolean wantsJsonLd = accept != null && accept.toLowerCase().contains(MEDIA_TYPE_JSON_LD);
    if (wantsJsonLd) body = body.withJsonLd();

    return Response.ok(body)
      .type(wantsJsonLd ? MEDIA_TYPE_JSON_LD : MediaType.APPLICATION_JSON)
      .build();
  }

  // ── URDF export ───────────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/export.urdf")
  @Produces(MEDIA_TYPE_URDF)
  @Operation(
    summary = "Export a scene as URDF XML.",
    description =
      "Walks the scene's frame tree and joints, emits URDF: one `<link>` per " +
      "`CoordinateFrame`, one `<joint>` per `Joint` with parent + child link " +
      "refs, `<origin>` from the child frame's local transform, `<axis>` from " +
      "the joint's axis triple, `<limit>` for REVOLUTE / PRISMATIC types. " +
      "Visual / collision / inertial blocks are NOT emitted — see " +
      "`UrdfExporter` Javadoc for the catalogue of deferred details.\n\n" +
      "Auth: any authenticated user."
  )
  @APIResponse(responseCode = "200", description = "URDF XML returned.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "No scene with that appId.")
  public Response exportUrdf(@PathParam("appId") @NotBlank String appId, @Context SecurityContext sc) {
    DigitalTwinScene scene = sceneGraphService.findScene(appId);
    if (scene == null) return Response.status(Response.Status.NOT_FOUND).build();
    Response gate = checkScenePermission(appId, AccessType.Read, sc);
    if (gate != null) return gate;
    String urdf = urdfExporter.export(
      scene,
      sceneGraphService.findFramesForScene(appId),
      sceneGraphService.findJointsForScene(appId)
    );
    return Response.ok(urdf, MEDIA_TYPE_URDF).build();
  }

  // ── USD export (stub) ─────────────────────────────────────────────────────

  @GET
  @Path("/{appId}/export.usd")
  @Operation(
    summary = "Export a scene as USD (placeholder).",
    description =
      "Returns 503 Service Unavailable with a Retry-After hint. The full USD " +
      "export ships in ISAAC-USD-EXPORT-1 (Isaac Sim round-trip per " +
      "`aidocs/data/85 §7`). This endpoint exists today so consumers can " +
      "discover the planned URL and the feature stays a backlog row, not a " +
      "missing route."
  )
  @APIResponse(responseCode = "503", description = "USD export not yet implemented (ISAAC-USD-EXPORT-1).")
  public Response exportUsd(@PathParam("appId") @NotBlank String appId) {
    return Response.status(503)
      .header("Retry-After", "ISAAC-USD-EXPORT-1")
      .entity("{\"detail\":\"USD export queued under ISAAC-USD-EXPORT-1; see aidocs/16 for status.\"}")
      .type(MediaType.APPLICATION_JSON)
      .build();
  }

  // ── POST create scene ─────────────────────────────────────────────────────

  @POST
  @Operation(
    summary = "Create a new empty scene graph.",
    description =
      "Mints a new `:DigitalTwinScene` with a fresh UUID v7 appId. Optional " +
      "body lets the caller set `name`, `description`, and `sourceFileAppId` " +
      "at creation time. The scene starts with no frames or joints — add them " +
      "via `POST /v2/scene-graphs/{appId}/frames` and `POST /v2/scene-graphs/" +
      "{appId}/joints` respectively. The first frame added becomes the root.\n\n" +
      "Records a CREATE `:Activity` with the standard PROV-O edges.\n\n" +
      "Auth: any authenticated user."
  )
  @APIResponse(
    responseCode = "201",
    description = "Scene created.",
    content = @Content(schema = @Schema(implementation = SceneGraphIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response create(
    CreateSceneRequestIO body,
    @HeaderParam(HEADER_AI_AGENT) String aiAgent,
    @Context SecurityContext sc
  ) {
    // SCENEGRAPH-PERMS-1 — create gate: if the caller supplied a
    // sourceFileAppId, they must hold Write on its parent Collection (same
    // shape as the scene → file → collection walk used for read/edit). When
    // no sourceFileAppId is supplied (hand-built scene), only instance-admin
    // can create — matches the post-create read/edit posture per the
    // hand-built-scene fail-closed rule.
    String caller = callerOf(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    boolean isAdmin = sc != null && sc.isUserInRole(SceneGraphPermissionService.INSTANCE_ADMIN_ROLE);
    String srcFileAppId = body == null ? null : body.getSourceFileAppId();
    if (srcFileAppId == null || srcFileAppId.isBlank()) {
      if (!isAdmin) {
        return Response.status(Response.Status.FORBIDDEN)
          .entity("{\"detail\":\"hand-built scenes (no sourceFileAppId) require instance-admin\"}")
          .build();
      }
    } else if (!scenePermissions.canCreateFromSourceFile(srcFileAppId, caller)) {
      return Response.status(Response.Status.FORBIDDEN)
        .entity("{\"detail\":\"caller lacks Write on the parent Collection of sourceFileAppId\"}")
        .build();
    }

    ProvenanceContext prov = provFor(sc, aiAgent);
    DigitalTwinScene scene = sceneGraphService.createScene(body, prov);
    handoffProvenance();
    SceneGraphIO io = new SceneGraphIO(scene, List.of(), List.of());
    return Response.status(Response.Status.CREATED).entity(io).build();
  }

  // ── POST add frame ────────────────────────────────────────────────────────

  @POST
  @Path("/{appId}/frames")
  @Operation(summary = "Add a frame to a scene.",
    description =
      "Adds a `:CoordinateFrame` to the scene. `parentFrameAppId` is required " +
      "unless the scene is currently empty (the first frame becomes the root). " +
      "Records an UPDATE `:Activity` for the parent scene plus a " +
      "`:WAS_DERIVED_FROM` edge to the prior activity on the same scene.")
  @APIResponse(
    responseCode = "201",
    description = "Frame added.",
    content = @Content(schema = @Schema(implementation = FrameIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Scene or parent frame not found.")
  public Response addFrame(
    @PathParam("appId") @NotBlank String appId,
    CreateFrameRequestIO body,
    @HeaderParam(HEADER_AI_AGENT) String aiAgent,
    @Context SecurityContext sc
  ) {
    if (body == null) body = new CreateFrameRequestIO();
    Response gate = checkScenePermission(appId, AccessType.Write, sc);
    if (gate != null) return gate;
    ProvenanceContext prov = provFor(sc, aiAgent);
    try {
      CoordinateFrame frame = sceneGraphService.addFrame(appId, body, prov);
      handoffProvenance();
      return Response.status(Response.Status.CREATED).entity(new FrameIO(frame)).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).entity(errorBody(nfe)).build();
    }
  }

  // ── PATCH frame ───────────────────────────────────────────────────────────

  @PATCH
  @Path("/{appId}/frames/{frameAppId}")
  @Operation(summary = "Patch a single frame's mutable fields.",
    description =
      "Mutates one of a frame's transform fields, parent pointer, kind, or " +
      "name. Fields absent from the body are left unchanged. Pass an empty " +
      "string for `parentFrameAppId` to make the frame a root (deletes the " +
      "`:HAS_PARENT_FRAME` edge).")
  @APIResponse(
    responseCode = "200",
    description = "Frame patched.",
    content = @Content(schema = @Schema(implementation = FrameIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Scene or frame not found.")
  public Response patchFrame(
    @PathParam("appId") @NotBlank String appId,
    @PathParam("frameAppId") @NotBlank String frameAppId,
    PatchFrameRequestIO body,
    @HeaderParam(HEADER_AI_AGENT) String aiAgent,
    @Context SecurityContext sc
  ) {
    if (body == null) body = new PatchFrameRequestIO();
    Response gate = checkScenePermission(appId, AccessType.Write, sc);
    if (gate != null) return gate;
    ProvenanceContext prov = provFor(sc, aiAgent);
    try {
      CoordinateFrame frame = sceneGraphService.patchFrame(appId, frameAppId, body, prov);
      handoffProvenance();
      return Response.ok(new FrameIO(frame)).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).entity(errorBody(nfe)).build();
    }
  }

  // ── DELETE frame ──────────────────────────────────────────────────────────

  @DELETE
  @Path("/{appId}/frames/{frameAppId}")
  @Operation(summary = "Delete a frame and its subtree.",
    description =
      "Hard-deletes the frame plus every descendant reachable via the " +
      "`:HAS_PARENT_FRAME` chain (the scaffold has no soft-delete field; see " +
      "`SceneGraphService` Javadoc). Joints touching any deleted frame are " +
      "also removed.")
  @APIResponse(responseCode = "204", description = "Frame subtree deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Scene or frame not found.")
  public Response deleteFrame(
    @PathParam("appId") @NotBlank String appId,
    @PathParam("frameAppId") @NotBlank String frameAppId,
    @HeaderParam(HEADER_AI_AGENT) String aiAgent,
    @Context SecurityContext sc
  ) {
    Response gate = checkScenePermission(appId, AccessType.Write, sc);
    if (gate != null) return gate;
    ProvenanceContext prov = provFor(sc, aiAgent);
    try {
      sceneGraphService.deleteFrameSubtree(appId, frameAppId, prov);
      handoffProvenance();
      return Response.noContent().build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).entity(errorBody(nfe)).build();
    }
  }

  // ── POST joint ────────────────────────────────────────────────────────────

  @POST
  @Path("/{appId}/joints")
  @Operation(summary = "Register a joint between two frames.")
  @APIResponse(
    responseCode = "201",
    description = "Joint added.",
    content = @Content(schema = @Schema(implementation = JointIO.class))
  )
  @APIResponse(responseCode = "400", description = "parentFrameAppId or childFrameAppId missing.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Scene or endpoint frame not found.")
  public Response addJoint(
    @PathParam("appId") @NotBlank String appId,
    CreateJointRequestIO body,
    @HeaderParam(HEADER_AI_AGENT) String aiAgent,
    @Context SecurityContext sc
  ) {
    if (body == null) body = new CreateJointRequestIO();
    Response gate = checkScenePermission(appId, AccessType.Write, sc);
    if (gate != null) return gate;
    ProvenanceContext prov = provFor(sc, aiAgent);
    try {
      Joint joint = sceneGraphService.addJoint(appId, body, prov);
      handoffProvenance();
      return Response.status(Response.Status.CREATED).entity(new JointIO(joint)).build();
    } catch (IllegalArgumentException iae) {
      return Response.status(Response.Status.BAD_REQUEST).entity(errorBody(iae)).build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).entity(errorBody(nfe)).build();
    }
  }

  // ── DELETE joint ──────────────────────────────────────────────────────────

  @DELETE
  @Path("/{appId}/joints/{jointAppId}")
  @Operation(summary = "Delete a joint.")
  @APIResponse(responseCode = "204", description = "Joint deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "404", description = "Scene or joint not found.")
  public Response deleteJoint(
    @PathParam("appId") @NotBlank String appId,
    @PathParam("jointAppId") @NotBlank String jointAppId,
    @HeaderParam(HEADER_AI_AGENT) String aiAgent,
    @Context SecurityContext sc
  ) {
    Response gate = checkScenePermission(appId, AccessType.Write, sc);
    if (gate != null) return gate;
    ProvenanceContext prov = provFor(sc, aiAgent);
    try {
      sceneGraphService.deleteJoint(appId, jointAppId, prov);
      handoffProvenance();
      return Response.noContent().build();
    } catch (NotFoundException nfe) {
      return Response.status(Response.Status.NOT_FOUND).entity(errorBody(nfe)).build();
    }
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private ProvenanceContext provFor(SecurityContext sc, String aiAgent) {
    return ProvenanceContext.from(callerOf(sc), aiAgent);
  }

  /** SCENEGRAPH-PERMS-1 — null-safe extraction of the authenticated username. */
  private static String callerOf(SecurityContext sc) {
    return sc != null && sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  /**
   * SCENEGRAPH-PERMS-1 — gate a per-scene operation. Returns {@code null}
   * when access is allowed (caller proceeds), or a short-circuit
   * {@link Response} ({@code 401} unauthenticated, {@code 403} forbidden)
   * to return immediately. Callers MUST have already verified the scene
   * exists before invoking this helper (it does not 404 — that's the
   * resource layer's job, before this gate, so missing-scene returns 404
   * not 403).
   */
  Response checkScenePermission(String sceneAppId, AccessType accessType, SecurityContext sc) {
    String caller = callerOf(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    boolean isAdmin = sc != null && sc.isUserInRole(SceneGraphPermissionService.INSTANCE_ADMIN_ROLE);
    if (!scenePermissions.isAllowed(sceneAppId, accessType, caller, isAdmin)) {
      return Response.status(Response.Status.FORBIDDEN)
        .entity("{\"detail\":\"caller lacks " + accessType + " on the parent Collection of this scene\"}")
        .build();
    }
    return null;
  }

  /**
   * Hand off skip-capture to the {@link ProvenanceCaptureFilter} so it does
   * not emit a duplicate generic Activity. Per the rule:
   * "handlers that record their own Activity hand off skip-capture".
   */
  private void handoffProvenance() {
    try {
      if (requestContext != null) {
        requestContext.setProperty(ProvenanceCaptureFilter.PROP_SKIP_CAPTURE, Boolean.TRUE);
      }
    } catch (RuntimeException e) {
      Log.debug("SCENEGRAPH: skip-capture handoff failed (non-fatal)", e);
    }
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

  private static String errorBody(Throwable t) {
    String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    return "{\"detail\":\"" + msg.replace("\"", "\\\"") + "\"}";
  }
}
