package de.dlr.shepard.v2.hdf.resources;

import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.data.hdf.io.HdfReferenceIO;
import de.dlr.shepard.data.hdf.io.HdfReferenceRequestIO;
import de.dlr.shepard.data.hdf.services.HdfReferenceService;
import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * A5c — {@code /v2/data-objects/{dataObjectAppId}/hdf-references} REST surface.
 *
 * <p>Three endpoints: list all references for a DataObject (GET),
 * create a new reference (POST), and delete by reference appId (DELETE).
 *
 * <p>The resource is short-circuited to 404 by Quarkus when
 * {@code shepard.hdf.enabled=false} — same {@link EndpointDisabled}
 * guard as {@link HdfContainerRest}. Mirrors the spatial-data pattern.
 *
 * <p>Permission model: READ on the DataObject's parent Collection for
 * GET; WRITE for POST and DELETE. Resolved by
 * {@link HdfReferenceService} via
 * {@code PermissionsService.isAccessAllowedForDataObjectAppId}.
 */
@EndpointDisabled(name = "shepard.hdf.enabled", stringValue = "false")
@Path("/v2/data-objects/{dataObjectAppId}/hdf-references")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(
  name = "HdfReference (v2)",
  description = "Per-DataObject anchors into HDF5 dataset paths (A5c). " +
  "Opt-in feature — administrator must set shepard.hdf.enabled=true."
)
public class HdfReferenceRest {

  @Inject
  HdfReferenceService service;

  // ─── GET /{dataObjectAppId}/hdf-references ────────────────────────────────

  @GET
  @Operation(summary = "List all HdfReferences for a DataObject.")
  @APIResponse(
    responseCode = "200",
    description = "List of references (may be empty).",
    content = @Content(schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.ARRAY,
                                        implementation = HdfReferenceIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller has no READ permission on the DataObject's Collection.")
  @APIResponse(responseCode = "404", description = "No DataObject with that appId, or feature toggled off.")
  public Response list(
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    String caller = securityContext.getUserPrincipal().getName();
    try {
      List<HdfReferenceIO> result = HdfReferenceIO.fromEntities(
        service.listForDataObject(dataObjectAppId, caller)
      );
      return Response.ok(result).build();
    } catch (NotFoundException | InvalidPathException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (ForbiddenException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
  }

  // ─── POST /{dataObjectAppId}/hdf-references ───────────────────────────────

  @POST
  @Transactional
  @Operation(summary = "Create a new HdfReference attached to a DataObject.")
  @APIResponse(
    responseCode = "201",
    description = "Created.",
    content = @Content(schema = @Schema(implementation = HdfReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — hdfContainerAppId or datasetPath missing/blank.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller has no WRITE permission on the DataObject's Collection.")
  @APIResponse(responseCode = "404", description = "DataObject or target HdfContainer not found, or feature toggled off.")
  public Response create(
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = HdfReferenceRequestIO.class)))
    HdfReferenceRequestIO body,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (body == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("request body is required").build();
    }
    if (body.getHdfContainerAppId() == null || body.getHdfContainerAppId().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("hdfContainerAppId is required").build();
    }
    if (body.getDatasetPath() == null || body.getDatasetPath().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("datasetPath is required").build();
    }
    String caller = securityContext.getUserPrincipal().getName();
    try {
      var created = service.create(dataObjectAppId, body, caller);
      return Response.status(Response.Status.CREATED).entity(new HdfReferenceIO(created)).build();
    } catch (NotFoundException | InvalidPathException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
    } catch (ForbiddenException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
  }

  // ─── DELETE /{dataObjectAppId}/hdf-references/{referenceAppId} ───────────

  @DELETE
  @Path("/{referenceAppId}")
  @Transactional
  @Operation(summary = "Delete an HdfReference.")
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller has no WRITE permission on the DataObject's Collection.")
  @APIResponse(responseCode = "404", description = "Reference not found or not owned by this DataObject, or feature toggled off.")
  public Response delete(
    @PathParam("dataObjectAppId") @NotBlank String dataObjectAppId,
    @PathParam("referenceAppId") @NotBlank String referenceAppId,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    String caller = securityContext.getUserPrincipal().getName();
    try {
      service.delete(dataObjectAppId, referenceAppId, caller);
      return Response.noContent().build();
    } catch (NotFoundException | InvalidPathException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (ForbiddenException e) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
  }
}
