package de.dlr.shepard.v2.spatial.promote;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static de.dlr.shepard.v2.common.ProblemResponse.problem;

/**
 * SPATIAL-UNIFY-004 — the in-context "Promote to spatial" surface.
 *
 * <p>{@code POST /v2/spatial/promote?fileReferenceAppId=…} takes an eligible
 * singleton FileReference (pointcloud / trajectory) and mints a
 * SpatialDataReference + its SpatialDataContainer behind it, enqueuing the
 * Python {@code spatial-importer} sidecar (via {@code promotionState=pending}
 * on the container). Idempotent: re-promoting the same file returns the
 * existing spatial reference (200) rather than minting a duplicate (201).
 *
 * <p>This is a per-file, one-click action (the operator decides when a costly
 * spatial container is spawned — NOT eager-on-upload, NOT a batch pass). Auth:
 * Write on the parent DataObject. The typed {@code :Activity} is captured
 * automatically by the core {@code ProvenanceCaptureFilter} (mutating 2xx).
 *
 * <p>Lives on the {@code /v2/} shelf (PLUGIN-V2-001); identifiers are appId
 * strings throughout — no numeric ids on the wire.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/spatial")
@RequestScoped
@Tag(name = "Spatial")
public class SpatialPromoteRest {

  @Inject
  SpatialPromoteService promoteService;

  @Inject
  SingletonFileReferenceService singletonFileReferenceService;

  @Inject
  PermissionsService permissionsService;

  @POST
  @Path("/promote")
  @Operation(
    operationId = "promoteFileReferenceToSpatial",
    summary = "Promote an eligible FileReference into a spatial reference (in-context, per-file).",
    description =
      "Given the appId of a singleton FileReference holding a pointcloud / " +
      "trajectory (.las/.laz/.ply/.e57/.pcd/.xyz/.pts or a named pointcloud/" +
      "trajectory file), mints a SpatialDataReference + its backing " +
      "SpatialDataContainer on the same DataObject and enqueues the spatial-" +
      "importer sidecar (container promotionState=pending). Idempotent: " +
      "re-promoting the same file returns the existing spatial reference (200).\n\n" +
      "Auth: Write on the parent DataObject."
  )
  @APIResponse(
    responseCode = "201",
    description = "Created a new spatial reference; body is the unified ReferenceV2IO.",
    content = @Content(schema = @Schema(implementation = ReferenceV2IO.class))
  )
  @APIResponse(
    responseCode = "200",
    description = "Already promoted; body is the existing spatial reference's ReferenceV2IO.",
    content = @Content(schema = @Schema(implementation = ReferenceV2IO.class))
  )
  @APIResponse(responseCode = "400", description = "Missing fileReferenceAppId or file is not an eligible spatial payload.")
  @APIResponse(responseCode = "401", description = "Authentication required.")
  @APIResponse(responseCode = "403", description = "Caller lacks Write on the parent DataObject.")
  @APIResponse(responseCode = "404", description = "No FileReference with that appId.")
  public Response promote(
    @Parameter(required = true, description = "UUID v7 appId of a FileReference whose content is an eligible spatial payload (e.g. GeoTIFF, GeoJSON, Shapefile). Returns 400 when absent or when the referenced file is not a supported spatial type.")
    @QueryParam("fileReferenceAppId") String fileReferenceAppId,
    @Context SecurityContext sc
  ) {
    String caller = sc.getUserPrincipal() != null ? sc.getUserPrincipal().getName() : null;
    if (caller == null) return problem(Response.Status.UNAUTHORIZED, "Authentication required");
    if (fileReferenceAppId == null || fileReferenceAppId.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
        .type("application/problem+json")
        .entity(new ProblemJson(
          "urn:shepard:error:validation",
          "Request validation failed",
          Response.Status.BAD_REQUEST.getStatusCode(),
          "fileReferenceAppId query parameter is required",
          null))
        .build();
    }

    // Permission gate: Write on the parent DataObject of the source FileReference.
    FileReference fileRef = singletonFileReferenceService.getByAppId(fileReferenceAppId);
    if (fileRef == null || fileRef.isDeleted()) {
      return problem(Response.Status.NOT_FOUND, "FileReference not found");
    }
    DataObject parent = fileRef.getDataObject();
    if (parent == null || parent.getAppId() == null) {
      return problem(Response.Status.NOT_FOUND, "DataObject not found");
    }
    if (!permissionsService.isAccessAllowedForDataObjectAppId(parent.getAppId(), AccessType.Write, caller)) {
      return problem(Response.Status.FORBIDDEN, "Insufficient permissions");
    }

    try {
      SpatialPromoteService.PromoteResult result = promoteService.promote(fileReferenceAppId);
      Response.Status status = result.created() ? Response.Status.CREATED : Response.Status.OK;
      return Response.status(status).entity(result.io()).build();
    } catch (BadRequestException bre) {
      return Response.status(Response.Status.BAD_REQUEST)
        .type("application/problem+json")
        .entity(new ProblemJson(
          "urn:shepard:error:validation",
          "Request validation failed",
          Response.Status.BAD_REQUEST.getStatusCode(),
          bre.getMessage(),
          null))
        .build();
    } catch (NotFoundException nfe) {
      return problem(Response.Status.NOT_FOUND, nfe.getMessage());
    }
  }

}
