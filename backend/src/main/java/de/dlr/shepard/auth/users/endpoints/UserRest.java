package de.dlr.shepard.auth.users.endpoints;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.security.JWTPrincipal;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path(Constants.SHEPARD_API + "/" + Constants.USERS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class UserRest {

  @Inject
  UserService userService;

  @Inject
  AuthenticationContext authenticationContext;

  @GET
  @Tag(name = Constants.USER)
  @Operation(description = "Get current user")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  public Response getCurrentUser() {
    // GETCURRENTUSER-GLOBAL-DEPTH0: this frozen upstream-compat surface emits the
    // caller's subscriptionIds/apiKeyIds on the wire via UserIO, so it needs the
    // depth-1 loader (the default getCurrentUser() is depth-0 and would empty them).
    User currentUser = userService.getCurrentUserWithCollections();
    UserIO io = new UserIO(currentUser);
    JWTPrincipal principal = authenticationContext.getPrincipal();
    if (principal != null) {
      io.setEffectiveRoles(Arrays.asList(principal.getRoles()));
    }
    return Response.ok(io).build();
  }

  @GET
  @Path("/{" + Constants.USERNAME + "}")
  @Tag(name = Constants.USER)
  @Operation(description = "Get user")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.USERNAME)
  public Response getUser(@PathParam(Constants.USERNAME) @NotBlank String username) {
    User user = userService.getUser(username);
    return Response.ok(new UserIO(user)).build();
  }
}
