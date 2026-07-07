package de.dlr.shepard.v2.admin.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * APISIMP-FEATURE-TOGGLE-CONFIG-UNIFY — tombstoned.
 *
 * <p>Use {@code GET /v2/admin/config/feature-toggles} (list + current state) and
 * {@code PATCH /v2/admin/config/feature-toggles} (toggle-name → boolean flat map)
 * instead.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/admin/runtime-toggles")
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class AdminFeaturesRest {

  private static final String GONE_DETAIL =
    "This endpoint has been unified under the generic config surface. " +
    "Use GET /v2/admin/config/feature-toggles to list toggles and " +
    "PATCH /v2/admin/config/feature-toggles with {\"toggle-name\": true/false} to mutate them.";

  private static final String SUCCESSOR = "/v2/admin/config/feature-toggles";

  private static Response gone() {
    return Response.status(Response.Status.GONE)
      .type("application/problem+json")
      .header("Location", SUCCESSOR)
      .header("Link", "<" + SUCCESSOR + ">; rel=\"successor-version\"")
      .entity(new ProblemJson(
        "urn:shepard:error:gone",
        "Gone",
        Response.Status.GONE.getStatusCode(),
        GONE_DETAIL,
        null))
      .build();
  }

  @GET
  @Operation(
    operationId = "listFeatureToggles",
    summary = "[GONE] List runtime feature toggles — use GET /v2/admin/config/feature-toggles.",
    deprecated = true
  )
  @APIResponse(responseCode = "410", description = "Endpoint removed. Use GET /v2/admin/config/feature-toggles.")
  public Response list() {
    return gone();
  }

  @PATCH
  @Path("/{name}")
  @Operation(
    operationId = "patchFeatureToggle",
    summary = "[GONE] Toggle a runtime feature flag — use PATCH /v2/admin/config/feature-toggles.",
    deprecated = true
  )
  @APIResponse(responseCode = "410", description = "Endpoint removed. Use PATCH /v2/admin/config/feature-toggles.")
  public Response patch(@PathParam("name") String name) {
    return gone();
  }
}
