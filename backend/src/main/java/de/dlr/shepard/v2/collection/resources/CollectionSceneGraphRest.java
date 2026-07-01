package de.dlr.shepard.v2.collection.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.collection.daos.CollectionHeroViewLinkDAO;
import de.dlr.shepard.v2.collection.io.CollectionHeroViewLinkIO;
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
 * V2CONV-B4 — {@code /v2/collections/{appId}/scene-graph} REST surface for the
 * Collection ↔ hero-view link.
 *
 * <p>The bespoke scene-graph subsystem dissolved into the generic
 * MAPPING_RECIPE mechanism (aidocs/platform/191 decision #2). This endpoint
 * keeps its path (so existing frontend callers don't break) but now stores and
 * returns a MAPPING_RECIPE {@code ShepardTemplate} appId — a "hero view" — in
 * place of a {@code :DigitalTwinScene}. The {@code :Collection.sceneGraphAppId}
 * scalar property is reused unchanged; only what it points <em>at</em> changes.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   GET    /v2/collections/{appId}/scene-graph — resolve the linked hero view
 *   PUT    /v2/collections/{appId}/scene-graph — link / replace
 *   DELETE /v2/collections/{appId}/scene-graph — unlink (does NOT delete the template)
 * </pre>
 *
 * <h2>Permission gate</h2>
 *
 * <p>{@code GET} requires {@link AccessType#Read} on the Collection.
 * {@code PUT} requires {@link AccessType#Write} on the Collection AND the target
 * to be an existing MAPPING_RECIPE template. {@code DELETE} requires
 * {@link AccessType#Write} on the Collection.
 *
 * <h2>Provenance</h2>
 *
 * <p>PUT and DELETE are cross-cutting mutations; the
 * {@code ProvenanceCaptureFilter} writes the {@code :Activity} row
 * automatically.
 */
@Path("/v2/collections/{appId}/scene-graph")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Authenticated
@Tag(name = "Collections")
public class CollectionSceneGraphRest {

  static final String MAPPING_RECIPE_KIND = "MAPPING_RECIPE";

  private static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/scene-graph.bad-request";
  private static final String PROBLEM_TYPE_NOT_FOUND = "/problems/scene-graph.not-found";
  private static final String PROBLEM_TYPE_UNPROCESSABLE = "/problems/scene-graph.unprocessable-entity";
  private static final String PROBLEM_TYPE_UNAUTHORIZED = "/problems/scene-graph.unauthorized";
  private static final String PROBLEM_TYPE_FORBIDDEN = "/problems/scene-graph.forbidden";

  @Inject CollectionHeroViewLinkDAO linkDAO;
  @Inject PermissionsService permissionsService;
  @Inject ShepardTemplateDAO templateDAO;

  // ── GET ────────────────────────────────────────────────────────────────────

  @GET
  @Operation(
    operationId = "getCollectionSceneGraph",
    summary = "Resolve the Collection's hero-view link.",
    description =
      "Returns the linked MAPPING_RECIPE template's identity tuple for the "
      + "Collection identified by `appId` (UUID v7). Auth: Read on the "
      + "Collection. 401 unauthenticated, 403 when the caller lacks Read, 404 "
      + "when the Collection does not exist OR no hero view is linked."
  )
  @APIResponse(
    responseCode = "200",
    description = "Linked hero-view identity tuple.",
    content = @Content(schema = @Schema(implementation = CollectionHeroViewLinkIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read on the Collection.")
  @APIResponse(responseCode = "404", description = "Collection not found or no hero view linked.")
  public Response get(@PathParam("appId") String collectionAppId, @Context SecurityContext sc) {
    String caller = callerOf(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
      Response.Status.UNAUTHORIZED, "caller identity unknown");

    Optional<Long> ogmId = linkDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Collection not found",
      Response.Status.NOT_FOUND, "no Collection with appId '" + collectionAppId + "'");
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Read, caller, 0L)) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Read access required",
        Response.Status.FORBIDDEN, "caller lacks Read on Collection '" + collectionAppId + "'");
    }

    Optional<String> linkedTemplateAppId = linkDAO.findLinkedTemplateAppId(collectionAppId);
    if (linkedTemplateAppId.isEmpty()) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "No hero view linked",
        Response.Status.NOT_FOUND, "Collection '" + collectionAppId + "' has no hero view linked");
    }

    CollectionHeroViewLinkIO body = new CollectionHeroViewLinkIO();
    body.setSceneGraphAppId(linkedTemplateAppId.get());
    templateDAO.findByAppId(linkedTemplateAppId.get()).ifPresent(t -> {
      body.setTemplateName(t.getName());
      body.setTemplateDescription(t.getDescription());
      body.setTemplateKind(t.getTemplateKind());
    });
    return Response.ok(body).build();
  }

  // ── PUT ────────────────────────────────────────────────────────────────────

  @PUT
  @Operation(
    summary = "Link / replace the Collection's hero view.",
    description =
      "Sets `:Collection.sceneGraphAppId` to the supplied MAPPING_RECIPE "
      + "template appId. Idempotent. Auth: Write on the Collection; the target "
      + "must be an existing MAPPING_RECIPE template. 400 missing appId, 401 "
      + "unauthenticated, 403 lacks Write, 404 Collection not found, 422 the "
      + "target is not a MAPPING_RECIPE template."
  )
  @APIResponse(
    responseCode = "200",
    description = "Hero view linked.",
    content = @Content(schema = @Schema(implementation = CollectionHeroViewLinkIO.class))
  )
  @APIResponse(responseCode = "400", description = "Body missing required sceneGraphAppId.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the Collection.")
  @APIResponse(responseCode = "404", description = "Collection or template not found.")
  @APIResponse(responseCode = "422", description = "Target template is not a MAPPING_RECIPE.")
  public Response link(
    @PathParam("appId") String collectionAppId,
    CollectionHeroViewLinkIO body,
    @Context SecurityContext sc
  ) {
    String caller = callerOf(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
      Response.Status.UNAUTHORIZED, "caller identity unknown");

    String templateAppId = body == null ? null : body.getSceneGraphAppId();
    if (templateAppId == null || templateAppId.isBlank()) {
      return problem(PROBLEM_TYPE_BAD_REQUEST, "Missing required field",
        Response.Status.BAD_REQUEST, "sceneGraphAppId (the MAPPING_RECIPE template appId) is required");
    }

    Optional<Long> ogmId = linkDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Collection not found",
      Response.Status.NOT_FOUND, "no Collection with appId '" + collectionAppId + "'");
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Write, caller, 0L)) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Write access required",
        Response.Status.FORBIDDEN, "caller lacks Write on Collection '" + collectionAppId + "'");
    }

    Optional<ShepardTemplate> template = templateDAO.findByAppId(templateAppId);
    if (template.isEmpty()) {
      return problem(PROBLEM_TYPE_NOT_FOUND, "Template not found",
        Response.Status.NOT_FOUND, "no template with that appId");
    }
    if (!MAPPING_RECIPE_KIND.equals(template.get().getTemplateKind())) {
      return problem422(PROBLEM_TYPE_UNPROCESSABLE, "Wrong template kind",
        "hero view must be a MAPPING_RECIPE template; kind=" + template.get().getTemplateKind());
    }

    boolean ok = linkDAO.link(collectionAppId, templateAppId);
    if (!ok) return problem(PROBLEM_TYPE_NOT_FOUND, "Collection not found",
      Response.Status.NOT_FOUND, "no Collection with appId '" + collectionAppId + "'");

    return get(collectionAppId, sc);
  }

  // ── DELETE ─────────────────────────────────────────────────────────────────

  @DELETE
  @Operation(
    summary = "Unlink the Collection's hero view.",
    description =
      "Clears `:Collection.sceneGraphAppId`. Does NOT delete the MAPPING_RECIPE "
      + "template. Idempotent. Auth: Write on the Collection."
  )
  @APIResponse(responseCode = "204", description = "Hero view unlinked (or was not linked).")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the Collection.")
  @APIResponse(responseCode = "404", description = "Collection not found.")
  public Response unlink(@PathParam("appId") String collectionAppId, @Context SecurityContext sc) {
    String caller = callerOf(sc);
    if (caller == null) return problem(PROBLEM_TYPE_UNAUTHORIZED, "Authentication required",
      Response.Status.UNAUTHORIZED, "caller identity unknown");

    Optional<Long> ogmId = linkDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return problem(PROBLEM_TYPE_NOT_FOUND, "Collection not found",
      Response.Status.NOT_FOUND, "no Collection with appId '" + collectionAppId + "'");
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Write, caller, 0L)) {
      return problem(PROBLEM_TYPE_FORBIDDEN, "Write access required",
        Response.Status.FORBIDDEN, "caller lacks Write on Collection '" + collectionAppId + "'");
    }

    linkDAO.unlink(collectionAppId);
    return Response.noContent().build();
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private static String callerOf(SecurityContext sc) {
    return sc != null && sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }

  /** For status codes not in the JAX-RS 3.1 Status enum (e.g. 422 Unprocessable Entity). */
  private static Response problem422(String type, String title, String detail) {
    ProblemJson body = new ProblemJson(type, title, 422, detail, null);
    return Response.status(422).type("application/problem+json").entity(body).build();
  }
}
