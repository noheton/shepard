package de.dlr.shepard.data.timeseries.migration.endpoints;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.data.timeseries.migration.io.MigrationProgressIO;
import de.dlr.shepard.data.timeseries.migration.services.MigrationProgressService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.SHEPARD_API + "/temp/migrations")
@RequestScoped
public class MigrationProgressRest {

  @Inject
  MigrationProgressService migrationProgressService;

  @GET
  @Path("/state")
  @Tag(name = "Migration")
  @Operation(description = "Get progress for all migration tasks")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      schema = @Schema(type = SchemaType.ARRAY, implementation = MigrationProgressIO.class)
    )
  )
  public Response getAll() {
    List<MigrationProgressIO> result = migrationProgressService
      .listAll()
      .stream()
      .map(MigrationProgressIO::new)
      .toList();
    return Response.ok(result).build();
  }

  @GET
  @Path("/{containerId}")
  @Tag(name = "Migration")
  @Operation(description = "Get progress for a specific container migration")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = MigrationProgressIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response getByContainer(@PathParam("containerId") long containerId) {
    var progress = migrationProgressService
      .getProgress(containerId)
      .orElseThrow(() -> new NotFoundException("No migration progress for container " + containerId));
    return Response.ok(new MigrationProgressIO(progress)).build();
  }
}
