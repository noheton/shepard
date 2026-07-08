package de.dlr.shepard.v2.me.resources;

import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
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
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

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
@Path("/v2/users/me/role-in/{collectionAppId}")
@RequestScoped
@Tag(name = "Me")
public class MeRoleInRest {

  private static final String PT_UNAUTHORIZED = "/problems/me.role-in.unauthorized";
  private static final String PT_NOT_FOUND = "/problems/me.role-in.not-found";
  private static final String PT_FORBIDDEN = "/problems/me.role-in.forbidden";

  @Inject
  CollectionPropertiesDAO collectionDAO;

  @Inject
  PermissionsService permissionsService;

  @GET
  @Operation(
    operationId = "roleIn",
    summary = "Caller's effective role in a Collection.",
    description =
      "Returns the caller's effective capabilities on the Collection identified by " +
      "`collectionAppId` as four booleans: `read`, `write`, `manage`, and `isInstanceAdmin`.\n\n" +
      "The booleans are additive: `manage` implies `write`, and `write` implies `read`. " +
      "A caller with the Owner or Manager role gets `manage=true`; a Writer gets " +
      "`write=true`; a Reader gets only `read=true`. Instance admins get " +
      "`isInstanceAdmin=true` regardless of their Collection-level role. The UI renders " +
      "the highest effective role as a chip (Owner / Editor / Reader) and shows a separate " +
      "Instance-Admin chip when applicable.\n\n" +
      "Existence-protection pattern: a 403 is returned when the Collection exists but the " +
      "caller has no roles and is not an instance admin, so a caller without access cannot " +
      "distinguish 'does not exist' from 'exists but forbidden' — 404 is reserved for " +
      "Collections that genuinely have no matching `appId`.\n\n" +
      "Auth: any authenticated user. The endpoint is intentionally unauthenticated-user " +
      "hostile — 401 is returned before any Collection lookup."
  )
  @APIResponse(
    responseCode = "200",
    description = "Caller has at least one effective role or is an instance admin. Body is a MeRoleInIO with fields `collectionAppId`, `read`, `write`, `manage`, `isInstanceAdmin`.",
    content = @Content(schema = @Schema(implementation = MeRoleInIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required (no JWT and no X-API-KEY).")
  @APIResponse(responseCode = "403", description = "Collection exists but the caller has no roles on it and is not an instance admin.")
  @APIResponse(responseCode = "404", description = "No Collection with the supplied appId.")
  public Response roleIn(@PathParam("collectionAppId") String collectionAppId, @Context SecurityContext securityContext) {
    String caller = securityContext.getUserPrincipal() != null ? securityContext.getUserPrincipal().getName() : null;
    if (caller == null) return problem(PT_UNAUTHORIZED, "Authentication required",
        Response.Status.UNAUTHORIZED, "Authentication is required to query role membership.");

    var ogmId = collectionDAO.findCollectionIdByAppId(collectionAppId);
    if (ogmId.isEmpty()) return problem(PT_NOT_FOUND, "Collection not found",
        Response.Status.NOT_FOUND, "No Collection with appId '" + collectionAppId + "'.");

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
      return problem(PT_FORBIDDEN, "Access denied",
          Response.Status.FORBIDDEN, "Caller has no roles on Collection '" + collectionAppId + "' and is not an instance admin.");
    }
    return Response.ok(new MeRoleInIO(collectionAppId, canRead, canWrite, canManage, isAdmin)).build();
  }

}
