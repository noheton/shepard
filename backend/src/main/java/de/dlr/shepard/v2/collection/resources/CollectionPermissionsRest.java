package de.dlr.shepard.v2.collection.resources;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
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
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code /v2/collections/{appId}/permissions} and
 * {@code /v2/collections/{appId}/roles} REST surface (BUG-COLL-APPID-ROUTE-PERMS-1).
 *
 * <p>Closes the last v1-only loophole in the Collection accessor family: the
 * three permissions operations (GET permissions, PUT permissions, GET roles)
 * previously required the Neo4j-OGM numeric {@code Long} primary key, forcing
 * every frontend caller to extract the legacy {@code id} field off the v2
 * response and use it for a v1 API call. Post-reset Collections (UUID v7 only,
 * no numeric {@code id}) silently degraded — permissions panel never loaded.
 *
 * <p>Auth pattern follows {@link CollectionPropertiesRest}: the resource
 * resolves {@code appId → OGM id} via {@link CollectionPropertiesDAO#findCollectionIdByAppId},
 * then delegates to the existing {@link PermissionsService} which owns the
 * permission-check logic. {@code GET /roles} requires {@link AccessType#Read};
 * {@code GET /permissions} and {@code PUT /permissions} require
 * {@link AccessType#Manage} (matching the v1 {@code assertIsAllowedToManageCollection}
 * gate in {@code CollectionService}).
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/collections/{appId}")
@RequestScoped
@Tag(name = "Collections")
public class CollectionPermissionsRest {

  private static final String PT_UNAUTHORIZED = "/problems/collection-permissions.unauthorized";
  private static final String PT_NOT_FOUND = "/problems/collection-permissions.not-found";
  private static final String PT_FORBIDDEN = "/problems/collection-permissions.forbidden";

  @Inject
  CollectionPropertiesDAO collectionPropertiesDAO;

  @Inject
  PermissionsService permissionsService;

  @GET
  @Path("/permissions")
  @Operation(
    operationId = "getCollectionPermissionsV2",
    summary = "[v2] Get permissions for a Collection.",
    description =
      "Returns the {@link PermissionsIO} for the Collection identified by `appId` (UUID v7). " +
      "Auth: Manage permission required (owner or manager role). Returns 401 when " +
      "unauthenticated; 403 when the caller lacks Manage access; 404 when no Collection " +
      "with that appId exists."
  )
  @APIResponse(
    responseCode = "200",
    description = "Permissions for the Collection.",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection found with the supplied appId.")
  public Response getPermissions(
    @PathParam("appId") String collectionAppId,
    @Context SecurityContext securityContext
  ) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return unauthorized();

    Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return notFound(collectionAppId);
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Manage, caller, 0L)) {
      return forbidden(collectionAppId);
    }

    Permissions perms = permissionsService.getPermissionsOfEntity(ogmId.get());
    return Response.ok(new PermissionsIO(perms)).build();
  }

  @PUT
  @Path("/permissions")
  @Operation(
    operationId = "editCollectionPermissionsV2",
    summary = "[v2] Update permissions for a Collection.",
    description =
      "Replaces the permissions of the Collection identified by `appId` (UUID v7) with the " +
      "supplied body. Auth: Manage permission required (owner or manager role). Returns 401 " +
      "when unauthenticated; 403 when the caller lacks Manage access; 404 when no Collection " +
      "with that appId exists."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated permissions for the Collection.",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Manage permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection found with the supplied appId.")
  public Response editPermissions(
    @PathParam("appId") String collectionAppId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO body,
    @Context SecurityContext securityContext
  ) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return unauthorized();

    Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return notFound(collectionAppId);
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Manage, caller, 0L)) {
      return forbidden(collectionAppId);
    }

    Permissions updated = permissionsService.updatePermissionsByNeo4jId(body, ogmId.get());
    return Response.ok(new PermissionsIO(updated)).build();
  }

  @GET
  @Path("/roles")
  @Operation(
    operationId = "getCollectionRolesV2",
    summary = "[v2] Get the caller's roles on a Collection.",
    description =
      "Returns the caller's {@link Roles} (owner/manager/writer/reader flags) on the " +
      "Collection identified by `appId` (UUID v7). Auth: Read permission required. Returns " +
      "401 when unauthenticated; 403 when the caller lacks Read access; 404 when no " +
      "Collection with that appId exists."
  )
  @APIResponse(
    responseCode = "200",
    description = "Caller's role flags on the Collection.",
    content = @Content(schema = @Schema(implementation = Roles.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT or X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the Collection.")
  @APIResponse(responseCode = "404", description = "No Collection found with the supplied appId.")
  public Response getRoles(
    @PathParam("appId") String collectionAppId,
    @Context SecurityContext securityContext
  ) {
    String caller = callerOrNull(securityContext);
    if (caller == null) return unauthorized();

    Optional<Long> ogmId = collectionPropertiesDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return notFound(collectionAppId);
    if (!permissionsService.isAccessTypeAllowedForUser(ogmId.get(), AccessType.Read, caller, 0L)) {
      return forbidden(collectionAppId);
    }

    Roles roles = permissionsService.getUserRolesOnEntity(ogmId.get(), caller);
    return Response.ok(roles).build();
  }

  private static String callerOrNull(SecurityContext sc) {
    return sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
  }

  private static Response unauthorized() {
    return problem(PT_UNAUTHORIZED, "Authentication required",
      Response.Status.UNAUTHORIZED, "caller identity unknown");
  }

  private static Response notFound(String appId) {
    return problem(PT_NOT_FOUND, "Collection not found",
      Response.Status.NOT_FOUND, "no Collection with appId '" + appId + "'");
  }

  private static Response forbidden(String appId) {
    return problem(PT_FORBIDDEN, "Insufficient permission",
      Response.Status.FORBIDDEN, "caller lacks required access on Collection '" + appId + "'");
  }

  private static Response problem(String type, String title, Response.Status status, String detail) {
    return Response.status(status).type("application/problem+json")
      .entity(new ProblemJson(type, title, status.getStatusCode(), detail, null)).build();
  }
}
