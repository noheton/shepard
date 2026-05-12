package de.dlr.shepard.v2.me.resources;

import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.v2.me.io.MeRoleInIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
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
 * {@code GET /v2/me/role-in/{collectionAppId}} — the data backing
 * U1c2's "Role in current context" header chip per {@code aidocs/51 §9.4}.
 *
 * <p>Returns the caller's per-Collection roles plus a flat
 * {@code isInstanceAdmin} flag so the chip can render in one fetch.
 *
 * <p>Authorization:
 * <ul>
 *   <li>401 — unauthenticated.</li>
 *   <li>404 — no Collection with the supplied {@code appId}.</li>
 *   <li>403 — Collection exists, caller has no Read/Write/Manage roles
 *       on it, and is not an instance admin. Mirrors the existence-
 *       protection pattern of {@code CollectionPropertiesRest} so a
 *       no-access caller can't probe which Collections exist.</li>
 *   <li>200 — at least one of {@code read} / {@code write} / {@code manage}
 *       / {@code isInstanceAdmin} is {@code true}.</li>
 * </ul>
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/me/role-in/{collectionAppId}")
@RequestScoped
@Tag(name = "Me")
public class MeRoleInRest {

  @Inject
  CollectionPropertiesDAO collectionDAO;

  @Inject
  PermissionsService permissionsService;

  @GET
  @Operation(
    summary = "Caller's role in a Collection — for the header role-in-context chip.",
    description = "Returns booleans for {read, write, manage, isInstanceAdmin}. The role-in-context UI " +
    "chip renders the highest-effective role (Owner / Editor / Reader) plus an Instance-Admin chip " +
    "alongside when applicable. See `aidocs/51 §9.4`."
  )
  @APIResponse(
    responseCode = "200",
    description = "Caller has at least one role (or is an instance admin).",
    content = @Content(schema = @Schema(implementation = MeRoleInIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller has no roles on the Collection and is not an instance admin.")
  @APIResponse(responseCode = "404", description = "No Collection with the supplied appId.")
  public Response roleIn(@PathParam("collectionAppId") String collectionAppId, @Context SecurityContext securityContext) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return Response.status(Response.Status.UNAUTHORIZED).build();

    var ogmId = collectionDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();

    Roles roles = permissionsService.getUserRolesOnEntity(ogmId.get(), caller);
    boolean isAdmin = securityContext.isUserInRole("instance-admin");

    // Roles map: owner & manager → manage; writer → write; reader → read.
    // owner currently implies manage in the legacy Permissions model
    // (owner = creator + admin-on-this-entity), but the wire shape only
    // surfaces the three orthogonal capabilities the chip cares about.
    boolean canManage = roles.isOwner() || roles.isManager();
    boolean canWrite = canManage || roles.isWriter();
    boolean canRead = canWrite || roles.isReader();

    if (!canRead && !canWrite && !canManage && !isAdmin) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(new MeRoleInIO(collectionAppId, canRead, canWrite, canManage, isAdmin)).build();
  }
}
