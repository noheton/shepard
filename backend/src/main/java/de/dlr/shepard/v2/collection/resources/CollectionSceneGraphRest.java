package de.dlr.shepard.v2.collection.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.collection.daos.CollectionSceneGraphLinkDAO;
import de.dlr.shepard.v2.collection.io.CollectionSceneGraphLinkIO;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphPermissionService;
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * COLL-SCENE-1 — {@code /v2/collections/{appId}/scene-graph} REST surface
 * for the Collection ↔ {@code :DigitalTwinScene} hero-scene link.
 *
 * <p>The Collection landing page renders a single primary scene-graph
 * (the MFFD robot cell, the LUMEN test bench, etc.) when this link is
 * set; otherwise it surfaces a "Link scene-graph" action to writers.
 * See {@code aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md} GAP-6
 * for the originating motivation.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   GET    /v2/collections/{appId}/scene-graph — resolve the linked scene
 *   PUT    /v2/collections/{appId}/scene-graph — link / replace
 *   DELETE /v2/collections/{appId}/scene-graph — unlink (does NOT delete the scene)
 * </pre>
 *
 * <h2>Permission gate</h2>
 *
 * <p>{@code GET} requires {@link AccessType#Read} on the Collection.
 * {@code PUT} requires {@link AccessType#Write} on the Collection AND
 * {@link SceneGraphPermissionService#isAllowed} {@link AccessType#Read}
 * on the target scene — a writer on Collection A must not be able to
 * blind-link a private scene B they cannot themselves read (advisor
 * note 2026-06-02). {@code DELETE} requires {@link AccessType#Write}
 * on the Collection.
 *
 * <h2>Independence from scene permissions</h2>
 *
 * <p>The Collection→Scene back-pointer is a weak render affordance, NOT
 * a new permission edge. {@link SceneGraphPermissionService} continues
 * to walk scene → FileReference → DataObject → Collection (the scene's
 * intrinsic anchor) regardless of what Collections have linked it as
 * their hero. Hand-built scenes pinned by admins stay admin-only.
 *
 * <h2>Provenance</h2>
 *
 * <p>PUT and DELETE are cross-cutting mutations; the
 * {@code ProvenanceCaptureFilter} writes the {@code :Activity} row
 * automatically (no handler-side {@code ProvenanceService.record()}
 * call — this resource has no domain-specific Activity enrichment to
 * justify bypassing the filter).
 */
@Path("/v2/collections/{appId}/scene-graph")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Collection scene-graph (v2)")
public class CollectionSceneGraphRest {

  @Inject CollectionSceneGraphLinkDAO linkDAO;
  @Inject PermissionsService permissionsService;
  @Inject SceneGraphService sceneGraphService;
  @Inject SceneGraphPermissionService scenePermissions;

  // ── GET ────────────────────────────────────────────────────────────────────

  @GET
  @Operation(
    summary = "Resolve the Collection's hero scene-graph link.",
    description =
      "Returns the linked {@code :DigitalTwinScene}'s identity tuple "
      + "(appId + name + description + root/source pointers + frame and "
      + "joint counts) for the Collection identified by `appId` (UUID "
      + "v7).\n\n"
      + "Auth: Read permission on the Collection. Returns 401 when "
      + "unauthenticated, 403 when the caller lacks Read access on the "
      + "Collection, 404 when the Collection does not exist OR when no "
      + "scene is currently linked.\n\n"
      + "Note: the response does NOT re-check the caller's permission on "
      + "the linked scene itself. The Collection's Read gate is the "
      + "policy boundary for this endpoint; the scene-side permission "
      + "walk applies on the dedicated `/v2/scene-graphs/{appId}` "
      + "surface."
  )
  @APIResponse(
    responseCode = "200",
    description = "Linked scene identity tuple.",
    content = @Content(schema = @Schema(implementation = CollectionSceneGraphLinkIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the Collection.")
  @APIResponse(responseCode = "404", description = "Collection not found or no scene linked.")
  public Response get(@PathParam("appId") String collectionAppId, @Context SecurityContext sc) {
    String caller = callerOf(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Optional<Long> ogmId = linkDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Read, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    Optional<String> linkedSceneAppId = linkDAO.findLinkedSceneAppId(collectionAppId);
    if (linkedSceneAppId.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    DigitalTwinScene scene = sceneGraphService.findScene(linkedSceneAppId.get());
    CollectionSceneGraphLinkIO body = new CollectionSceneGraphLinkIO();
    body.setSceneGraphAppId(linkedSceneAppId.get());
    if (scene != null) {
      body.setName(scene.getName());
      body.setDescription(scene.getDescription());
      body.setRootFrameAppId(scene.getRootFrameAppId());
      body.setSourceFileAppId(scene.getSourceFileAppId());
      // Count walks reuse SceneGraphService — null-safe when the scene was
      // hard-deleted out from under us (the link is a scalar pointer, not
      // an OGM cascade edge; a dangling pointer renders as "name+counts
      // absent" rather than an outright 500).
      try {
        body.setFrameCount((long) sceneGraphService.findFramesForScene(linkedSceneAppId.get()).size());
        body.setJointCount((long) sceneGraphService.findJointsForScene(linkedSceneAppId.get()).size());
      } catch (RuntimeException ignored) {
        // Fire-and-forget: counts are decorative. Render the appId either way.
      }
    }
    return Response.ok(body).build();
  }

  // ── PUT ────────────────────────────────────────────────────────────────────

  @PUT
  @Operation(
    summary = "Link / replace the Collection's hero scene-graph.",
    description =
      "Sets `:Collection.sceneGraphAppId` to the supplied scene appId. "
      + "Idempotent: calling PUT with the same scene as already linked "
      + "is a no-op write that returns 200.\n\n"
      + "Auth: Write permission on the Collection AND Read permission "
      + "on the target scene (the two-sided gate prevents a writer on "
      + "Collection A from blind-linking a private scene B they could "
      + "not themselves read). Returns 401 unauthenticated, 403 when "
      + "either gate denies, 404 when the Collection or scene does not "
      + "exist."
  )
  @APIResponse(
    responseCode = "200",
    description = "Scene linked.",
    content = @Content(schema = @Schema(implementation = CollectionSceneGraphLinkIO.class))
  )
  @APIResponse(responseCode = "400", description = "Body missing required sceneGraphAppId.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on Collection or Read on the scene.")
  @APIResponse(responseCode = "404", description = "Collection or scene not found.")
  public Response link(
    @PathParam("appId") String collectionAppId,
    CollectionSceneGraphLinkIO body,
    @Context SecurityContext sc
  ) {
    String caller = callerOf(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    String sceneAppId = body == null ? null : body.getSceneGraphAppId();
    if (sceneAppId == null || sceneAppId.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("{\"detail\":\"sceneGraphAppId is required\"}")
        .build();
    }

    Optional<Long> ogmId = linkDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Write, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    // Target scene must exist.
    DigitalTwinScene scene = sceneGraphService.findScene(sceneAppId);
    if (scene == null) {
      return Response.status(Response.Status.NOT_FOUND)
        .entity("{\"detail\":\"no scene with that appId\"}")
        .build();
    }

    // Two-sided gate — caller must be able to read the target scene (per
    // SceneGraphPermissionService's own walk). Prevents blind-link of
    // private scenes.
    boolean isAdmin = sc != null && sc.isUserInRole(SceneGraphPermissionService.INSTANCE_ADMIN_ROLE);
    if (!scenePermissions.isAllowed(sceneAppId, AccessType.Read, caller, isAdmin)) {
      return Response.status(Response.Status.FORBIDDEN)
        .entity("{\"detail\":\"caller lacks Read on the target scene\"}")
        .build();
    }

    boolean ok = linkDAO.link(collectionAppId, sceneAppId);
    if (!ok) return Response.status(Response.Status.NOT_FOUND).build();

    // Return the GET shape with the freshly-linked scene's identity tuple
    // so the frontend doesn't need a follow-up round-trip.
    return get(collectionAppId, sc);
  }

  // ── DELETE ─────────────────────────────────────────────────────────────────

  @DELETE
  @Operation(
    summary = "Unlink the Collection's hero scene-graph.",
    description =
      "Clears `:Collection.sceneGraphAppId`. Does NOT delete the "
      + "`:DigitalTwinScene` entity — it remains addressable via "
      + "`/v2/scene-graphs/{appId}`.\n\n"
      + "Idempotent: a DELETE on an unlinked Collection returns 204.\n\n"
      + "Auth: Write permission on the Collection."
  )
  @APIResponse(responseCode = "204", description = "Scene unlinked (or was not linked).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the Collection.")
  @APIResponse(responseCode = "404", description = "Collection not found.")
  public Response unlink(@PathParam("appId") String collectionAppId, @Context SecurityContext sc) {
    String caller = callerOf(sc);
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Optional<Long> ogmId = linkDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Write, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    linkDAO.unlink(collectionAppId);
    return Response.noContent().build();
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private static String callerOf(SecurityContext sc) {
    return sc != null && sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }
}
