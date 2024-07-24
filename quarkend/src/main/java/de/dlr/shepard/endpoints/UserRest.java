package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.neo4Core.services.UserService;
import de.dlr.shepard.security.JWTPrincipal;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path(Constants.USERS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserRest {

  private UserService userService = new UserService();

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.USER)
  @Operation(description = "Get current user")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserIO.class))
  )
  public Response getCurrentUser() {
    JWTPrincipal principal = (JWTPrincipal) securityContext.getUserPrincipal();
    User currentUser = userService.getUser(principal.getUsername());
    return currentUser == null
      ? Response.status(Status.NOT_FOUND).build()
      : Response.ok(new UserIO(currentUser)).build();
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
  public Response getUser(@PathParam(Constants.USERNAME) String username) {
    User user = userService.getUser(username);
    return user == null ? Response.status(Status.NOT_FOUND).build() : Response.ok(new UserIO(user)).build();
  }
}
