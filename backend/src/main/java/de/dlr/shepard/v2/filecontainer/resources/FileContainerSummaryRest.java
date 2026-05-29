package de.dlr.shepard.v2.filecontainer.resources;

import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.v2.containers.io.ContainerCardinalitySummaryIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * UI21-SIZEBAR-DATA — per-kind cardinality summary for a FileContainer.
 *
 * <p>Route: {@code GET /v2/file-containers/{containerId}/summary}
 *
 * <p>Returns the number of files currently linked to the container.
 * The file list is loaded from the Neo4j graph via
 * {@link FileContainerService#getContainer(long)}, which eagerly hydrates
 * the {@code files} relationship at depth 1; the count is then a simple
 * Java {@code List.size()} call — no extra query.
 *
 * <p>This drives the sizebar metric in the frontend container list
 * (UI21-SIZEBAR-DATA).
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/file-containers")
@RequestScoped
@Tag(name = "File containers — cardinality summary (UI21-SIZEBAR-DATA)")
public class FileContainerSummaryRest {

  @Inject
  FileContainerService fileContainerService;

  @GET
  @Path("/{containerId}/summary")
  @Operation(
    summary = "Cardinality summary for a FileContainer.",
    description = "Returns the file count (number of ShepardFile nodes linked to this container) " +
      "and the current timestamp. Used by the frontend container-list sizebar (UI21-SIZEBAR-DATA). " +
      "The file list is loaded from Neo4j at depth-1 (already cached by the permission check) — " +
      "no additional query. Requires Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "Cardinality summary for the container.",
    content = @Content(schema = @Schema(implementation = ContainerCardinalitySummaryIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No FileContainer with that id.")
  public Response getSummary(@PathParam("containerId") long containerId) {
    // getContainer performs the read-permission check and loads files at depth 1.
    FileContainer container = fileContainerService.getContainer(containerId);

    long fileCount = container.getFiles() != null ? (long) container.getFiles().size() : 0L;

    return Response.ok(new ContainerCardinalitySummaryIO(
      fileCount,
      Instant.now().toString()
    )).build();
  }
}
