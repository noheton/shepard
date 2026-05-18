package de.dlr.shepard.v2.admin.users;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.auth.users.validation.OrcidValidator;
import de.dlr.shepard.common.util.Constants;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * U1a-admin — instance-admin route to set ORCID on someone else's
 * shepard User record.
 *
 * <p>Self-PATCH ({@code PATCH /v2/users/me}) is the normal flow; this
 * endpoint is for one-shot seed scripts and admin remediation. The
 * user identified by {@code username} must already exist (i.e. they
 * have logged in at least once so {@code UserinfoService} has minted
 * their User node).
 *
 * <p>Wire shape: PATCH body is the same record shape as the user's own
 * `PatchMeIO` for the orcid field, intentionally minimal — admins
 * shouldn't be rewriting other fields like display name or email
 * from this surface.
 */
@Path("/v2/admin/users/{" + Constants.USERNAME + "}/orcid")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin — user ORCID preseed (U1a-admin)")
public class AdminUserOrcidRest {

  @Inject
  UserService userService;

  public record AdminOrcidPatchIO(String orcid) {}

  @PATCH
  @Operation(
    summary = "Set or clear the ORCID id on another user's shepard record.",
    description = "Admin-only one-shot preseed for demo / migration scenarios. " +
    "Passing null clears the orcid; a non-null value must be a valid ORCID " +
    "(16 digits with mod 11-2 checksum) — same validation as the user's own " +
    "self-PATCH. The target user must already exist (i.e. they have logged " +
    "in at least once so the User node is minted)."
  )
  @APIResponse(responseCode = "204", description = "ORCID updated.")
  @APIResponse(
    responseCode = "400",
    description = "Bad request — invalid ORCID format."
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  @APIResponse(responseCode = "404", description = "No user with that username.")
  public Response patch(
    @PathParam(Constants.USERNAME) String username,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = AdminOrcidPatchIO.class))
    ) AdminOrcidPatchIO patch
  ) {
    if (patch.orcid() != null && !OrcidValidator.isValid(patch.orcid())) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("{\"error\":\"Invalid ORCID format. Expected 16 digits in groups of 4 with mod 11-2 checksum.\"}")
        .build();
    }
    var optionalUser = userService.getUserOptional(username);
    if (optionalUser.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    User user = optionalUser.get();
    user.setOrcid(patch.orcid());
    userService.createOrUpdateUser(user);
    return Response.status(Response.Status.NO_CONTENT).build();
  }
}
