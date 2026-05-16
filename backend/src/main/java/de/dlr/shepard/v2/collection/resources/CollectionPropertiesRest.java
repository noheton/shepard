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
    description = "Returns the :CollectionProperties row attached to the Collection. Creates one " +
    "lazily if missing (e.g. legacy Collection not yet backfilled). Requires Read permission " +
    "on the Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "The Collection's properties.",
    content = @Content(schema = @Schema(implementation = CollectionPropertiesIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
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
    description = "Every body field is nullable; only supplied fields apply. Requires Manage " +
    "permission on the Collection."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated properties.",
    content = @Content(schema = @Schema(implementation = CollectionPropertiesIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
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
    }
    CollectionProperties saved = propertiesDAO.createOrUpdate(props);
    return Response.ok(CollectionPropertiesIO.from(saved)).build();
  }
}
