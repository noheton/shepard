package de.dlr.shepard.v2.structureddatacontainer.resources;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * CC1b — list DataObjects that link to a StructuredDataContainer via their references.
 *
 * <p>Route:
 * <ul>
 *   <li>{@code GET /v2/structured-data-containers/{containerId}/linked-data-objects}
 *       — returns the distinct DataObjects whose StructuredDataReference nodes point
 *       at this container. Requires Read permission on the container.</li>
 * </ul>
 *
 * <p>Uses the numeric OGM id so the frontend can pass the route param directly.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/structured-data-containers")
@RequestScoped
@Tag(name = "Structured data containers — linked DataObjects (CC1b)")
public class StructuredDataContainerLinkedDataObjectsRest {

  @Inject
  StructuredDataContainerService structuredDataContainerService;

  @GET
  @Path("/{containerId}/linked-data-objects")
  @Operation(
    summary = "List DataObjects linked to this StructuredDataContainer.",
    description = "Returns the distinct DataObjects whose StructuredDataReference nodes point at this " +
    "container. Requires Read permission on the container."
  )
  @APIResponse(
    responseCode = "200",
    description = "List of linked DataObjects (may be empty).",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = DataObjectIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Read permission on the container.")
  @APIResponse(responseCode = "404", description = "No StructuredDataContainer with that id.")
  public Response getLinkedDataObjects(
    @PathParam("containerId") long containerId
  ) {
    List<DataObject> dataObjects = structuredDataContainerService.findLinkedDataObjectsById(containerId);
    List<DataObjectIO> result = new ArrayList<>(dataObjects.size());
    for (DataObject dataObject : dataObjects) {
      result.add(new DataObjectIO(dataObject));
    }
    return Response.ok(result).build();
  }
}
