package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.ApiKeyIO;
import de.dlr.shepard.neo4Core.io.ApiKeyWithJWTIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Response;

public interface ApiKeyRest {
  @Tag(name = Constants.APIKEY)
  @Operation(description = "Get all api keys")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiKeyIO.class)))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response getAllApiKeys(String username);

  @Tag(name = Constants.APIKEY)
  @Operation(description = "Get api key")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = ApiKeyIO.class))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response getApiKey(String username, String apiKeyUid);

  @Tag(name = Constants.APIKEY)
  @Operation(description = "Create a new api key")
  @ApiResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = ApiKeyWithJWTIO.class))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response createApiKey(
    String username,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = ApiKeyIO.class))
    ) @Valid ApiKeyIO apiKey
  );

  @Tag(name = Constants.APIKEY)
  @Operation(description = "Delete api key")
  @ApiResponse(description = "deleted", responseCode = "204")
  @ApiResponse(description = "not found", responseCode = "404")
  Response deleteApiKey(String username, String apiKeyUid);
}
