package de.dlr.shepard.auth.users.endpoints;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.io.UserIO;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.Constants;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path(Constants.USERS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class UserRest {

  @Inject
  UserService userService;

  @GET
  @Tag(name = Constants.USER)
  @Operation(description = "Get current user")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserIO.class))
  )
  public Response getCurrentUser() {
    User currentUser = userService.getCurrentUser();
    return Response.ok(new UserIO(currentUser)).build();
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
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.USERNAME)
  public Response getUser(@PathParam(Constants.USERNAME) String username) {
    User user = userService.getUser(username);
    return Response.ok(new UserIO(user)).build();
  }
}
