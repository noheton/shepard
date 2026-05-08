package de.dlr.shepard.auth.apikey.endpoints;

import de.dlr.shepard.auth.apikey.entities.ApiKey;
import de.dlr.shepard.auth.apikey.io.ApiKeyIO;
import de.dlr.shepard.auth.apikey.io.ApiKeyWithJWTIO;
import de.dlr.shepard.auth.apikey.services.ApiKeyService;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.common.util.Constants;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.SHEPARD_API + "/" + Constants.USERS + "/{" + Constants.USERNAME + "}/" + Constants.APIKEYS)
@RequestScoped
public class ApiKeyRest {

  @Inject
  ApiKeyService apiKeyService;

  @Context
  private UriInfo uriInfo;

  @GET
  @Tag(name = Constants.APIKEY)
  @Operation(description = "Get all api keys")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      schema = @Schema(description = "The search result page", type = SchemaType.ARRAY, implementation = ApiKeyIO.class)
    )
  )
  @APIResponse(description = "bad request", responseCode = "400")
  @APIResponse(description = "not authorized", responseCode = "401")
  @APIResponse(description = "forbidden", responseCode = "403")
  @Parameter(name = Constants.USERNAME, required = true)
  public Response getAllApiKeys(@PathParam(Constants.USERNAME) @NotBlank String username) {
    var apiKeys = apiKeyService.getAllApiKeys(username);
    var result = new ArrayList<ApiKeyIO>(apiKeys.size());

    for (var key : apiKeys) {
      result.add(new ApiKeyIO(key));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.APIKEY_UID + "}")
  @Tag(name = Constants.APIKEY)
  @Operation(description = "Get api key")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = ApiKeyIO.class))
  )
  @APIResponse(description = "bad request", responseCode = "400")
  @APIResponse(description = "not authorized", responseCode = "401")
  @APIResponse(description = "forbidden", responseCode = "403")
  @Parameter(name = Constants.USERNAME, required = true)
  @Parameter(name = Constants.APIKEY_UID, required = true)
  public Response getApiKey(
    @PathParam(Constants.USERNAME) @NotBlank String username,
    @PathParam(Constants.APIKEY_UID) @NotBlank @org.hibernate.validator.constraints.UUID String apiKeyUid
  ) {
    UUID uid;
    try {
      uid = UUID.fromString(apiKeyUid);
    } catch (IllegalArgumentException e) {
      Log.errorf("The given api key uid has an invalid format: %s", apiKeyUid);
      throw new InvalidRequestException("The given api key uid has an invalid format");
    }
    ApiKey apiKey = apiKeyService.getApiKey(username, uid);
    return Response.ok(new ApiKeyIO(apiKey)).build();
  }

  @POST
  @Tag(name = Constants.APIKEY)
  @Operation(description = "Create a new api key")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = ApiKeyWithJWTIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @APIResponse(description = "bad request", responseCode = "400")
  @APIResponse(description = "not authorized", responseCode = "401")
  @APIResponse(description = "forbidden", responseCode = "403")
  @Parameter(name = Constants.USERNAME, required = true)
  public Response createApiKey(
    @PathParam(Constants.USERNAME) @NotBlank String username,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = ApiKeyIO.class))
    ) @Valid ApiKeyIO apiKey
  ) {
    ApiKey created = apiKeyService.createApiKey(apiKey, username, uriInfo.getBaseUri().toString());
    return Response.ok(new ApiKeyWithJWTIO(created)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.APIKEY_UID + "}")
  @Tag(name = Constants.APIKEY)
  @Operation(description = "Delete api key")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @APIResponse(description = "bad request", responseCode = "400")
  @APIResponse(description = "not authorized", responseCode = "401")
  @APIResponse(description = "forbidden", responseCode = "403")
  @Parameter(name = Constants.USERNAME, required = true)
  @Parameter(name = Constants.APIKEY_UID, required = true)
  public Response deleteApiKey(
    @PathParam(Constants.USERNAME) @NotBlank String username,
    @PathParam(Constants.APIKEY_UID) @NotBlank @org.hibernate.validator.constraints.UUID String apiKeyUid
  ) {
    UUID uid;
    try {
      uid = UUID.fromString(apiKeyUid);
    } catch (IllegalArgumentException e) {
      Log.errorf("The given api key uid has an invalid format: %s", apiKeyUid);
      throw new InvalidRequestException("The given api key uid has an invalid format");
    }
    return apiKeyService.deleteApiKey(username, uid)
      ? Response.status(Status.NO_CONTENT).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }
}
