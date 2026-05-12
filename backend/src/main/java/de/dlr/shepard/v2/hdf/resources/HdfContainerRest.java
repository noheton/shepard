package de.dlr.shepard.v2.hdf.resources;

import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.data.hdf.io.HdfContainerIO;
import de.dlr.shepard.data.hdf.services.HdfContainerService;
import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * A5a Phase 1 — {@code /v2/hdf-containers/...} REST surface
 * ({@code aidocs/35-hdf5-hsds-implementation-design.md} §7).
 *
 * <p>Three endpoints in this slice: create, read, delete. The
 * read/write data-path mirroring of HSDS's
 * {@code /datasets/{id}/value} surface, the {@code HdfReference}
 * per-DataObject anchor, the byte-identical download fallback, and
 * the shared-Keycloak token relay all arrive in later phases
 * (A5b–A5e).
 *
 * <p>The whole resource is short-circuited to 404 by Quarkus when
 * {@code shepard.hdf.enabled=false} — that's the
 * {@link EndpointDisabled} annotation. Mirrors the spatial-data
 * pattern (see {@code SpatialDataPointRest}).
 */
@EndpointDisabled(name = "shepard.hdf.enabled", stringValue = "false")
@Path("/v2/hdf-containers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(
  name = "HdfContainer (v2)",
  description = "HDF5/HSDS containers (Phase 1: HSDS sidecar + create/read/delete). " +
  "Opt-in feature — administrator must set shepard.hdf.enabled=true. " +
  "Otherwise endpoints return 404."
)
public class HdfContainerRest {

  @Inject
  HdfContainerService service;

  @GET
  @Path("/{appId}")
  @Operation(summary = "Get one HdfContainer by appId.")
  @APIResponse(
    responseCode = "200",
    description = "The container.",
    content = @Content(schema = @Schema(implementation = HdfContainerIO.class))
  )
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller has no read permission.")
  @APIResponse(responseCode = "404", description = "No container with that appId, or feature toggled off.")
  public Response get(@PathParam("appId") @NotBlank String appId, @Context SecurityContext securityContext) {
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      var container = service.getContainerByAppId(appId);
      return Response.ok(new HdfContainerIO(container)).build();
    } catch (InvalidPathException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @POST
  @Transactional
  @Operation(summary = "Create a new HdfContainer (provisions the HSDS domain via the sidecar).")
  @APIResponse(
    responseCode = "201",
    description = "Created.",
    content = @Content(schema = @Schema(implementation = HdfContainerIO.class))
  )
  @APIResponse(responseCode = "400", description = "Bad request — name missing or body invalid.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "503", description = "HSDS sidecar unreachable.")
  public Response create(
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = HdfContainerIO.class)))
    @Valid HdfContainerIO body,
    @Context SecurityContext securityContext
  ) {
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (body == null || body.getName() == null || body.getName().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity("name is required").build();
    }
    var created = service.createContainer(body);
    return Response.status(Response.Status.CREATED).entity(new HdfContainerIO(created)).build();
  }

  @DELETE
  @Path("/{appId}")
  @Transactional
  @Operation(summary = "Delete the HdfContainer (drops the HSDS domain).")
  @APIResponse(responseCode = "204", description = "Deleted.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller is not the owner.")
  @APIResponse(responseCode = "404", description = "No container with that appId, or feature toggled off.")
  @APIResponse(responseCode = "503", description = "HSDS sidecar unreachable.")
  public Response delete(@PathParam("appId") @NotBlank String appId, @Context SecurityContext securityContext) {
    if (securityContext.getUserPrincipal() == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      service.deleteContainerByAppId(appId);
      return Response.noContent().build();
    } catch (InvalidPathException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }
}
