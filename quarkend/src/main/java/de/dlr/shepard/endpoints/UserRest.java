package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.UserIO;
import de.dlr.shepard.util.Constants;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.ws.rs.core.Response;

public interface UserRest {
  @Tag(name = Constants.USER)
  @Operation(description = "Get current user")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserIO.class))
  )
  Response getCurrentUser();

  @Tag(name = Constants.USER)
  @Operation(description = "Get user")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getUser(String username);
}
