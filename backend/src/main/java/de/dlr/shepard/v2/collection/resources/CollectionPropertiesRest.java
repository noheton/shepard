package de.dlr.shepard.v2.collection.resources;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.collection.entities.CollectionProperties;
import de.dlr.shepard.v2.collection.io.CollectionPropertiesIO;
import de.dlr.shepard.v2.collection.io.PatchCollectionPropertiesIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
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
 * {@code /v2/collections/{appId}/properties} REST surface for the
 * per-Collection settings node (CP1b, per {@code aidocs/58 §5}).
 *
 * <p>Auth + permissions: read requires {@link AccessType#Read} on the
 * Collection; write requires {@link AccessType#Manage}. Both delegate
 * to the existing {@link PermissionsService} (the same path the legacy
 * {@code /shepard/api/collections/...} surface uses).
 *
 * <p>Lookup is by Collection {@code appId} — the {@code /v2/} surface's
 * canonical identifier per {@code aidocs/25} / {@code aidocs/56}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/collections/{appId}/properties")
@RequestScoped
@Tag(name = "Collection properties (v2)")
public class CollectionPropertiesRest {

  @Inject
  CollectionPropertiesDAO propertiesDAO;

  @Inject
  PermissionsService permissionsService;

  @GET
  @Operation(
    summary = "Read the per-Collection settings node.",
    description =
      "Returns the `:CollectionProperties` node attached to the Collection identified by " +
      "`appId` (UUID v7). The node is created lazily on first access if it does not yet " +
      "exist — this covers legacy Collections created before CP1b shipped.\n\n" +
      "Fields in the response include `webdavVisible` (boolean, controls whether the " +
      "Collection is mounted in the WebDAV tree), `defaultOntologyUri` (the ontology " +
      "pre-selected in the annotation UI), `uiDefaultsJson` (arbitrary JSON string " +
      "the frontend stores per-Collection UI preferences in), and " +
      "`publishToHelmholtzKG` (boolean, opt-in to Helmholtz Knowledge Graph harvest).\n\n" +
      "Auth: Read permission on the parent Collection. Returns 403 when the caller " +
      "lacks Read access; 404 when no Collection with that appId exists.\n\n" +
      "Next step: `PATCH /v2/collections/{appId}/properties` to flip any of the " +
      "settings (requires Manage permission)."
  )
  @APIResponse(
    responseCode = "200",
    description = "The Collection's :CollectionProperties settings node, created lazily if missing.",
    content = @Content(schema = @Schema(implementation = CollectionPropertiesIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection found with the supplied appId.")
  public Response read(@PathParam("appId") String collectionAppId, @Context SecurityContext securityContext) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Optional<Long> ogmId = propertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Read, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    CollectionProperties props = propertiesDAO.ensureFor(collectionAppId);
    return Response.ok(CollectionPropertiesIO.from(props)).build();
  }

  @PATCH
  @Operation(
    summary = "Partial-update the per-Collection settings node.",
    description =
      "Applies a partial update to the `:CollectionProperties` node for the Collection " +
      "identified by `appId` (UUID v7). Every body field is optional; only the fields " +
      "present in the request are changed — absent fields are left unchanged.\n\n" +
      "Patchable fields: `webdavVisible` (boolean), `defaultOntologyUri` (string, URI of " +
      "the ontology to pre-select in the annotation UI), `uiDefaultsJson` (arbitrary JSON " +
      "string for frontend per-Collection preferences), `publishToHelmholtzKG` (boolean).\n\n" +
      "Example: enable WebDAV visibility — `{\"webdavVisible\": true}`. Example: set a " +
      "default ontology — `{\"defaultOntologyUri\": \"https://example.org/onto/v1\"}`. " +
      "Example: opt into Helmholtz KG publish — `{\"publishToHelmholtzKG\": true}`.\n\n" +
      "Auth: Manage permission on the parent Collection (stronger than Write). Returns " +
      "403 when the caller only has Read or Write access; 404 when no Collection with " +
      "that appId exists.\n\n" +
      "Side effects: the `:CollectionProperties` node is created lazily if it does not " +
      "yet exist before applying the patch. Changes to `publishToHelmholtzKG` take effect " +
      "on the next Helmholtz KG harvest cycle."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated :CollectionProperties node with all fields after the patch applied.",
    content = @Content(schema = @Schema(implementation = CollectionPropertiesIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection found with the supplied appId.")
  public Response patch(
    @PathParam("appId") String collectionAppId,
    PatchCollectionPropertiesIO body,
    @Context SecurityContext securityContext
  ) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    Optional<Long> ogmId = propertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Manage, caller, 0L)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    CollectionProperties props = propertiesDAO.ensureFor(collectionAppId);
    if (body != null) {
      if (body.getWebdavVisible() != null) props.setWebdavVisible(body.getWebdavVisible());
      if (body.getDefaultOntologyUri() != null) props.setDefaultOntologyUri(body.getDefaultOntologyUri());
      if (body.getUiDefaultsJson() != null) props.setUiDefaultsJson(body.getUiDefaultsJson());
      if (body.getPublishToHelmholtzKG() != null) props.setPublishToHelmholtzKG(body.getPublishToHelmholtzKG());
    }
    CollectionProperties saved = propertiesDAO.createOrUpdate(props);
    return Response.ok(CollectionPropertiesIO.from(saved)).build();
  }
}
