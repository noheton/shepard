package de.dlr.shepard.common.versionz;

import de.dlr.shepard.common.util.Constants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path(Constants.VERSIONZ)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class VersionzRest {

  @Inject
  @ConfigProperty(name = "shepard.version")
  String shepardVersion;

  @GET
  @Tag(name = Constants.VERSIONZ)
  @Operation(description = "Get shepard version")
  @APIResponse(
    description = "Version of the running shepard instance",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = VersionzIO.class))
  )
  public Response getShepardVersion() {
    return Response.ok(new VersionzIO(shepardVersion)).build();
  }
}
