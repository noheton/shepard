package de.dlr.shepard.v2.users.resources;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.auth.users.daos.UserDAO;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.auth.users.validation.OrcidValidator;
import de.dlr.shepard.common.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code PATCH /v2/users/me} — partial update of the caller's User
 * record per {@code aidocs/16 U1a}. RFC 7396 JSON Merge Patch
 * semantics: fields present in the body replace the corresponding
 * fields, fields absent are preserved, explicit JSON {@code null}
 * clears the field.
 *
 * <p>v1 (U1a) only accepts the {@code orcid} field. Later U1
 * sub-slices grow the patchable surface (displayName at U1b, avatar
 * at U1e, etc.).
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/users/me")
@RequestScoped
@Tag(name = "Me")
public class MeRest {

  @Inject
  UserService userService;

  @Inject
  UserDAO userDAO;

  @PATCH
  @Consumes({ Constants.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON })
  @Operation(
    summary = "Partial-update the caller's User record.",
    description = "RFC 7396 JSON Merge Patch. v1 (U1a) accepts `orcid` only. " +
    "ORCID format: `NNNN-NNNN-NNNN-NNN[N|X]` (ISO 7064 mod 11-2 checked). " +
    "Empty-string `orcid` clears the field. Unknown body fields are ignored."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated User.",
    content = @Content(schema = @Schema(implementation = UserIO.class))
  )
  @APIResponse(responseCode = "400", description = "Body is not a JSON object, or ORCID failed checksum.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  public Response patchMe(
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = de.dlr.shepard.v2.users.io.PatchMeIO.class))) JsonNode patch,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) return Response.status(Response.Status.UNAUTHORIZED).build();
    if (patch == null || !patch.isObject()) {
      return Response.status(Response.Status.BAD_REQUEST)
        .entity("PATCH body must be a JSON object (RFC 7396 JSON Merge Patch)")
        .build();
    }

    User current = userService.getCurrentUser();

    if (patch.has("orcid")) {
      JsonNode orcidNode = patch.get("orcid");
      if (orcidNode.isNull()) {
        current.setOrcid(null);
      } else if (orcidNode.isTextual()) {
        String value = orcidNode.asText();
        if (value.isEmpty()) {
          current.setOrcid(null);
        } else if (!OrcidValidator.isValid(value)) {
          return Response.status(Response.Status.BAD_REQUEST)
            .entity("orcid must match NNNN-NNNN-NNNN-NNN[N|X] and pass the ISO 7064 mod 11-2 checksum")
            .build();
        } else {
          current.setOrcid(value);
        }
      } else {
        return Response.status(Response.Status.BAD_REQUEST).entity("orcid must be a string or null").build();
      }
    }
    // Other patchable fields land in later U1 sub-slices; unknown
    // body keys are ignored per RFC 7396's open-world semantics.

    User saved = userDAO.createOrUpdate(current);
    return Response.ok(new UserIO(saved)).build();
  }
}
