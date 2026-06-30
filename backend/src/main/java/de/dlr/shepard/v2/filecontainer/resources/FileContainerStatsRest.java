package de.dlr.shepard.v2.filecontainer.resources;

import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.v2.filecontainer.io.FileContainerStatsIO;
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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * UI21-SIZEBAR-DATA — cardinality stats for a FileContainer.
 *
 * <p>Route: {@code GET /v2/file-containers/{containerId}/stats}
 *
 * <p>Returns the number of {@link de.dlr.shepard.data.file.entities.ShepardFile} nodes
 * attached to the container. Used by the /containers list sizebar to display a
 * domain-meaningful scale indicator (file count) instead of the CC1e referenced-by proxy.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/file-containers")
@RequestScoped
@Tag(name = "File containers — cardinality stats (UI21-SIZEBAR-DATA)")
public class FileContainerStatsRest {

  @Inject
  FileContainerService fileContainerService;

  @GET
  @Path("/{containerId}/stats")
  @Operation(
    summary = "File count for a FileContainer.",
    description = "Returns the number of files stored in this container. " +
      "Requires Read permission on the container. " +
      "Used by the /containers list sizebar to show a domain-meaningful scale indicator."
  )
  @APIResponse(
    responseCode = "200",
    description = "Cardinality stats for the container.",
    content = @Content(schema = @Schema(implementation = FileContainerStatsIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No FileContainer with that id.")
  public Response getStats(@PathParam("containerId") long containerId) {
    // Permission check — throws 403/404 if not accessible.
    FileContainer container = fileContainerService.getContainer(containerId);

    long fileCount = container.getFiles() != null ? container.getFiles().size() : 0L;

    return Response.ok(new FileContainerStatsIO(fileCount)).build();
  }
}
