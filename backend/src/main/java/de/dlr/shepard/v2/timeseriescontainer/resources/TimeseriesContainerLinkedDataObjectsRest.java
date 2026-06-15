package de.dlr.shepard.v2.timeseriescontainer.resources;

import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.integrity.SafeDeleteConflict;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.List;
import java.util.Objects;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * DI1 — safe-delete for a TimeseriesContainer (refuses to orphan active references).
 *
 * <p>Route:
 * <ul>
 *   <li>{@code DELETE /v2/timeseries-containers/{containerAppId}} — safe delete;
 *       409 unless {@code ?force=true} when the container has active references.</li>
 * </ul>
 *
 * <p>The {@code linked-data-objects} listing previously served here was
 * collapsed onto the unified {@code GET /v2/containers/{appId}/linked-data-objects}
 * surface (APISIMP-CONT-LDO-UNIFY); only the kind-specific safe-delete remains.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/timeseries-containers")
@RequestScoped
@Tag(name = "Timeseries container safe delete")
public class TimeseriesContainerLinkedDataObjectsRest {

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  /**
   * DI1 — safe delete: refuses to delete if active references exist.
   *
   * <p>Returns {@code 204} when the container has no active references, or
   * when called with {@code ?force=true}. Returns {@code 409} with a
   * {@link SafeDeleteConflict} body otherwise. The upstream
   * {@code /shepard/api/timeseriesContainers/{id}} DELETE keeps its
   * orphan-references-silently semantics (per the API freeze).
   */
  @DELETE
  @Path("/{containerAppId}")
  @Operation(
    operationId = "deleteTimeseriesContainerSafely",
    summary = "Safely delete this TimeseriesContainer.",
    description = "Refuses with 409 if active references exist unless ?force=true is supplied. " +
    "Use this in preference to the upstream /shepard/api/timeseriesContainers/{id} DELETE, which " +
    "always deletes and silently orphans any surviving references."
  )
  @APIResponse(responseCode = "204", description = "Container deleted.")
  @APIResponse(
    responseCode = "409",
    description = "Container has active references; retry with ?force=true to delete anyway.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = SafeDeleteConflict.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write permission on the container.")
  @APIResponse(responseCode = "404", description = "No TimeseriesContainer with that appId.")
  public Response safeDeleteContainer(
    @PathParam("containerAppId") String containerAppId,
    @QueryParam("force") @DefaultValue("false") boolean force
  ) {
    var container = timeseriesContainerService.getContainerByAppId(containerAppId);
    if (!force) {
      List<DataObject> linked = timeseriesContainerService.findLinkedDataObjectsByAppId(containerAppId);
      if (!linked.isEmpty()) {
        List<String> sample = linked.stream()
          .map(DataObject::getAppId)
          .filter(Objects::nonNull)
          .limit(SafeDeleteConflict.SAMPLE_LIMIT)
          .toList();
        return Response.status(Status.CONFLICT)
          .type("application/problem+json")
          .entity(new SafeDeleteConflict(linked.size(), sample))
          .build();
      }
    }
    timeseriesContainerService.deleteContainer(container.getId());
    return Response.status(Status.NO_CONTENT).build();
  }
}
