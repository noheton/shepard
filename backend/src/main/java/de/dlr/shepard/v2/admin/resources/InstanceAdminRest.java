package de.dlr.shepard.v2.admin.resources;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.ApiError;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.io.GrantInstanceAdminIO;
import de.dlr.shepard.v2.admin.io.InstanceAdminGrantIO;
import de.dlr.shepard.v2.admin.io.PermissionAuditEntryIO;
import de.dlr.shepard.v2.admin.services.InstanceAdminService;
import de.dlr.shepard.v2.admin.services.PermissionAuditService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * The {@code /v2/admin/instance-admins} + {@code /v2/admin/permission-audit}
 * endpoints, per {@code aidocs/51 §10}. All gated by
 * {@code @RolesAllowed("instance-admin")} — the role-check routes
 * through {@code JWTSecurityContext.isUserInRole} which consults the
 * dual-source-resolved principal.
 *
 * <p>This class lives under {@code de.dlr.shepard.v2.admin.resources}
 * per the P4 namespace convention; the {@code @Path} string starts
 * with {@code /v2/admin/...}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/admin")
@RequestScoped
public class InstanceAdminRest {

  @Inject
  InstanceAdminService instanceAdminService;

  @Inject
  PermissionAuditService permissionAuditService;

  @Inject
  AuthenticationContext authenticationContext;

  /**
   * Manual role-check fallback: in addition to {@code @RolesAllowed},
   * each method calls {@link #requireInstanceAdmin(SecurityContext)}
   * for defence-in-depth (the runtime gate doesn't engage if
   * {@code quarkus-security} isn't on the classpath, but the manual
   * check always does).
   */
  private static void requireInstanceAdmin(SecurityContext securityContext) {
    if (securityContext == null || !securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)) {
      throw new InvalidAuthException("instance-admin role required");
    }
  }

  @GET
  @Path("/instance-admins")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Tag(name = "Admin")
  @Operation(description = "List Neo4j-side instance-admin grants (with audit metadata).")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      schema = @Schema(type = SchemaType.ARRAY, implementation = InstanceAdminGrantIO.class)
    )
  )
  @APIResponse(description = "forbidden", responseCode = "403")
  public Response listInstanceAdmins(@Context SecurityContext securityContext) {
    requireInstanceAdmin(securityContext);
    List<InstanceAdminGrantIO> grants = instanceAdminService.listInstanceAdmins();
    return Response.ok(grants).build();
  }

  @POST
  @Path("/instance-admins")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Tag(name = "Admin")
  @Operation(description = "Grant the instance-admin role to a user.")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = InstanceAdminGrantIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @APIResponse(description = "forbidden", responseCode = "403")
  public Response grantInstanceAdmin(
    @Context SecurityContext securityContext,
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = GrantInstanceAdminIO.class))) @Valid GrantInstanceAdminIO body
  ) {
    requireInstanceAdmin(securityContext);
    String grantedBy = authenticationContext == null ? null : authenticationContext.getCurrentUserName();
    InstanceAdminGrantIO grant = instanceAdminService.grantInstanceAdmin(body.getUsername(), grantedBy);
    return Response.status(Status.CREATED).entity(grant).build();
  }

  @DELETE
  @Path("/instance-admins/{username}")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Tag(name = "Admin")
  @Operation(description = "Revoke the Neo4j-side instance-admin grant from a user.")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @APIResponse(description = "forbidden", responseCode = "403")
  public Response revokeInstanceAdmin(
    @Context SecurityContext securityContext,
    @PathParam("username") @NotBlank String username
  ) {
    requireInstanceAdmin(securityContext);
    boolean revoked = instanceAdminService.revokeInstanceAdmin(username);
    if (!revoked) {
      return Response.status(Status.NOT_FOUND)
        .entity(new ApiError(Status.NOT_FOUND.getStatusCode(), "NotFound", "No Neo4j-side instance-admin grant for user '" + username + "'"))
        .build();
    }
    return Response.status(Status.NO_CONTENT).build();
  }

  @GET
  @Path("/permission-audit")
  @RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
  @Tag(name = "Admin")
  @Operation(description = "List BasicEntity nodes that lack a :has_permissions edge (post-C3).")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      schema = @Schema(type = SchemaType.ARRAY, implementation = PermissionAuditEntryIO.class)
    )
  )
  @APIResponse(description = "forbidden", responseCode = "403")
  public Response permissionAudit(@Context SecurityContext securityContext) {
    requireInstanceAdmin(securityContext);
    List<PermissionAuditEntryIO> orphans = permissionAuditService.listOrphans();
    return Response.ok(orphans).build();
  }
}
