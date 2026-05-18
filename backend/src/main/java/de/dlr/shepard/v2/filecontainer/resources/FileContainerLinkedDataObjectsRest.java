package de.dlr.shepard.v2.filecontainer.resources;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.v2.integrity.SafeDeleteConflict;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * CC1b — list DataObjects that link to a FileContainer via their references.
 *
 * <p>Route:
 * <ul>
 *   <li>{@code GET /v2/file-containers/{containerId}/linked-data-objects}
 *       — returns the distinct DataObjects whose references (SingletonFileReference or
 *       FileReference/FileBundleReference) point at this container. Requires Read
 *       permission on the container.</li>
 * </ul>
 *
 * <p>Uses the numeric OGM id (same as the legacy {@code /shepard/api/fileContainers/{id}}
 * surface) so the frontend can pass the route param directly without waiting for the
 * container's appId to be loaded.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/file-containers")
@RequestScoped
@Tag(name = "File containers — linked DataObjects (CC1b)")
public class FileContainerLinkedDataObjectsRest {

  @Inject
  FileContainerService fileContainerService;

  @GET
  @Path("/{containerId}/linked-data-objects")
  @Operation(
    summary = "List DataObjects linked to this FileContainer.",
    description = "Returns the distinct DataObjects whose references (SingletonFileReference or " +
    "FileReference/FileBundleReference) point at this container. Requires Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of linked DataObjects (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = DataObjectIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No FileContainer with that id.")
  public Response getLinkedDataObjects(
    @PathParam("containerId") long containerId
  ) {
    List<DataObject> dataObjects = fileContainerService.findLinkedDataObjectsById(containerId);
    List<DataObjectIO> result = new ArrayList<>(dataObjects.size());
    for (DataObject dataObject : dataObjects) {
      result.add(new DataObjectIO(dataObject));
    }
    return Response.ok(result).build();
  }

  /** DI1 — safe delete: refuses with 409 if active references exist unless ?force=true. */
  @DELETE
  @Path("/{containerId}")
  @Operation(
    summary = "Safely delete this FileContainer.",
    description = "Refuses with 409 if active references exist unless ?force=true is supplied. " +
    "Use this in preference to the upstream /shepard/api/fileContainers/{id} DELETE, which " +
    "always deletes and silently orphans any surviving references."
  )
  @APIResponse(responseCode = "204", description = "Container deleted.")
  @APIResponse(
    responseCode = "409",
    description = "Container has active references; retry with ?force=true to delete anyway.",
    content = @Content(schema = @Schema(implementation = SafeDeleteConflict.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No FileContainer with that id.")
  public Response safeDeleteContainer(
    @PathParam("containerId") long containerId,
    @QueryParam("force") @DefaultValue("false") boolean force
  ) {
    if (!force) {
      List<DataObject> linked = fileContainerService.findLinkedDataObjectsById(containerId);
      if (!linked.isEmpty()) {
        List<String> sample = linked.stream()
          .map(DataObject::getAppId)
          .filter(Objects::nonNull)
          .limit(SafeDeleteConflict.SAMPLE_LIMIT)
          .toList();
        return Response.status(Status.CONFLICT)
          .entity(new SafeDeleteConflict(linked.size(), sample))
          .build();
      }
    }
    fileContainerService.deleteContainer(containerId);
    return Response.status(Status.NO_CONTENT).build();
  }
}
