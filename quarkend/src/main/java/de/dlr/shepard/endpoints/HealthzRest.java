package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.HealthzIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.core.Response;

public interface HealthzRest {
  @Tag(name = Constants.HEALTHZ)
  @Operation(description = "Get server health")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = HealthzIO.class))
  )
  @ApiResponse(
    description = "not ok",
    responseCode = "503",
    content = @Content(schema = @Schema(implementation = HealthzIO.class))
  )
  Response getServerHealth();
}
