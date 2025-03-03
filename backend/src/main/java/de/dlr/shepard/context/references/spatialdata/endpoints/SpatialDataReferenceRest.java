package de.dlr.shepard.context.references.spatialdata.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.context.references.spatialdata.io.SpatialDataReferenceIO;
import de.dlr.shepard.context.references.spatialdata.services.SpatialDataReferenceService;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(
  Constants.COLLECTIONS +
  "/{" +
  Constants.COLLECTION_ID +
  "}/" +
  Constants.DATA_OBJECTS +
  "/{" +
  Constants.DATA_OBJECT_ID +
  "}/" +
  Constants.SPATIAL_DATA_REFERENCES
)
@RequestScoped
public class SpatialDataReferenceRest {

  private SpatialDataReferenceService spatialDataReferenceService;

  @Context
  private SecurityContext securityContext;

  //SpatialDataReferenceService() {}

  @Inject
  public SpatialDataReferenceRest(SpatialDataReferenceService spatialDataReferenceService) {
    this.spatialDataReferenceService = spatialDataReferenceService;
  }

  @GET
  @Tag(name = Constants.SPATIAL_DATA_REFERENCE)
  @Operation(description = "Get all spatialData references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SpatialDataReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getAllSpatialDataReferences(
    @PathParam(Constants.COLLECTION_ID) long collectionId, //Why is this here?
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    var references = spatialDataReferenceService.getAllReferencesByDataObjectShepardId(dataObjectId, versionUID);
    var result = new ArrayList<SpatialDataReferenceIO>(references.size());
    for (var ref : references) {
      result.add(new SpatialDataReferenceIO(ref));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.SPATIAL_DATA_REFERENCE_ID + "}")
  @Tag(name = Constants.SPATIAL_DATA_REFERENCE)
  @Operation(description = "Get spatialData reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = SpatialDataReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.SPATIAL_DATA_REFERENCE_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getSpatialDataReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.SPATIAL_DATA_REFERENCE_ID) long spatialDataReferenceId,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    var result = spatialDataReferenceService.getReferenceByShepardId(spatialDataReferenceId, versionUID);

    return Response.ok(new SpatialDataReferenceIO(result)).build();
  }

  @POST
  @Subscribable
  @Tag(name = Constants.SPATIAL_DATA_REFERENCE)
  @Operation(description = "Create a new spatialData reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = SpatialDataReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response createSpatialDataReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId, //Why is this here?
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = SpatialDataReferenceIO.class))
    ) @Valid SpatialDataReferenceIO spatialDataReference
  ) {
    var result = spatialDataReferenceService.createReferenceByShepardId(
      dataObjectId,
      spatialDataReference,
      securityContext.getUserPrincipal().getName()
    );

    return Response.ok(new SpatialDataReferenceIO(result)).status(Response.Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.SPATIAL_DATA_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.SPATIAL_DATA_REFERENCE)
  @Operation(description = "Delete spatialData reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.SPATIAL_DATA_REFERENCE_ID)
  public Response deleteSpatialDataReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.SPATIAL_DATA_REFERENCE_ID) long spatialDataReferenceId
  ) {
    var result = spatialDataReferenceService.deleteReferenceByShepardId(
      spatialDataReferenceId,
      securityContext.getUserPrincipal().getName()
    );

    return result
      ? Response.status(Response.Status.NO_CONTENT).build()
      : Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.SPATIAL_DATA_REFERENCE_ID + "}/" + Constants.PAYLOAD)
  @Tag(name = Constants.SPATIAL_DATA_REFERENCE)
  @Operation(description = "Get spatialData reference payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SpatialDataPointIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.SPATIAL_DATA_REFERENCE_ID)
  public Response getSpatialDataPayload(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.SPATIAL_DATA_REFERENCE_ID) long spatialDataReferenceId
  ) {
    return Response.ok(spatialDataReferenceService.getReferencePayload(spatialDataReferenceId)).build();
  }
}
