package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.ApiKeyIO;
import de.dlr.shepard.neo4Core.io.ApiKeyWithJWTIO;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

public interface ApiKeyRest {
  @Tag(name = Constants.APIKEY)
  @Operation(description = "Get all api keys")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      schema = @Schema(description = "The search result page", type = SchemaType.ARRAY, implementation = ApiKeyIO.class)
    )
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllApiKeys(String username);

  @Tag(name = Constants.APIKEY)
  @Operation(description = "Get api key")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = ApiKeyIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getApiKey(String username, String apiKeyUid);

  @Tag(name = Constants.APIKEY)
  @Operation(description = "Create a new api key")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = ApiKeyWithJWTIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createApiKey(
    String username,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = ApiKeyIO.class))
    ) @Valid ApiKeyIO apiKey
  );

  @Tag(name = Constants.APIKEY)
  @Operation(description = "Delete api key")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteApiKey(String username, String apiKeyUid);
}
