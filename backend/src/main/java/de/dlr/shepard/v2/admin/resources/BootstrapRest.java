package de.dlr.shepard.v2.admin.resources;

import de.dlr.shepard.v2.admin.io.BootstrapRequestIO;
import de.dlr.shepard.v2.admin.services.BootstrapService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * The {@code POST /v2/admin/bootstrap} endpoint —
 * <b>unauthenticated</b> by design (the token is the auth proof).
 * Designed in {@code aidocs/51 §5.2}.
 *
 * <p>Listed in {@link de.dlr.shepard.common.filters.PublicEndpointRegistry}
 * so the {@code JWTFilter} doesn't reject the call before the body
 * arrives.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/admin/bootstrap")
@RequestScoped
public class BootstrapRest {

  @Inject
  BootstrapService bootstrapService;

  @POST
  @Tag(name = "Admin")
  @Operation(
    description = "Consume the one-shot bootstrap token + grant instance-admin to a user. " +
    "Unauthenticated — the token is the auth proof. Token-replay returns 403."
  )
  @APIResponse(description = "granted", responseCode = "201")
  @APIResponse(description = "forbidden — token mismatch or already consumed", responseCode = "403")
  @APIResponse(description = "user not found", responseCode = "404")
  public Response bootstrap(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = BootstrapRequestIO.class))
    ) @Valid BootstrapRequestIO body
  ) {
    String username = bootstrapService.consumeBootstrap(body.getToken(), body.getUsername());
    return Response.status(Status.CREATED).entity(Map.of("username", username, "role", "instance-admin")).build();
  }
}
