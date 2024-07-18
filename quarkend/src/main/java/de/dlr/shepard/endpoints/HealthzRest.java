package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.HealthzIO;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

public interface HealthzRest {
  @Tag(name = Constants.HEALTHZ)
  @Operation(description = "Get server health")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = HealthzIO.class))
  )
  @APIResponse(
    description = "not ok",
    responseCode = "503",
    content = @Content(schema = @Schema(implementation = HealthzIO.class))
  )
  Response getServerHealth();
}
