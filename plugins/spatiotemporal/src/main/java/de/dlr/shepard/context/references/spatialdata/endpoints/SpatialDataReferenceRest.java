package de.dlr.shepard.context.references.spatialdata.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.spatialdata.io.SpatialDataReferenceIO;
import de.dlr.shepard.context.references.spatialdata.services.SpatialDataReferenceService;
import de.dlr.shepard.data.spatialdata.io.SpatialDataPointIO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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
  Constants.SHEPARD_API +
  "/" +
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

  @Inject
  SpatialDataReferenceService spatialDataReferenceService;

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.SPATIAL_DATA_REFERENCE)
  @Operation(description = "Get all spatial data references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = SpatialDataReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getAllSpatialDataReferences(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }

    var references = spatialDataReferenceService.getAllReferencesByDataObjectId(
      collectionId,
      dataObjectId,
      versionUUID
    );
    List<SpatialDataReferenceIO> result = references
      .stream()
      .map(SpatialDataReferenceIO::new)
      .collect(Collectors.toList());
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.SPATIAL_DATA_REFERENCE_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getSpatialDataReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.SPATIAL_DATA_REFERENCE_ID) @NotNull @PositiveOrZero Long spatialDataReferenceId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }

    var result = spatialDataReferenceService.getReference(
      collectionId,
      dataObjectId,
      spatialDataReferenceId,
      versionUUID
    );

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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response createSpatialDataReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @RequestBody(
      required = true,
      description = "For more examples take a look at [Get spatial data by container id](#/spatialDataContainer/getSpatialDataPoints).",
      content = @Content(schema = @Schema(implementation = SpatialDataReferenceIO.class))
    ) @Valid SpatialDataReferenceIO spatialDataReference
  ) {
    var result = spatialDataReferenceService.createReference(collectionId, dataObjectId, spatialDataReference);
    return Response.ok(new SpatialDataReferenceIO(result)).status(Response.Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.SPATIAL_DATA_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.SPATIAL_DATA_REFERENCE)
  @Operation(description = "Delete spatialData reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.SPATIAL_DATA_REFERENCE_ID)
  public Response deleteSpatialDataReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.SPATIAL_DATA_REFERENCE_ID) @NotNull @PositiveOrZero Long spatialDataReferenceId
  ) {
    spatialDataReferenceService.deleteReference(collectionId, dataObjectId, spatialDataReferenceId);
    return Response.status(Response.Status.NO_CONTENT).build();
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.SPATIAL_DATA_REFERENCE_ID)
  public Response getSpatialDataPayload(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.SPATIAL_DATA_REFERENCE_ID) @NotNull @PositiveOrZero Long spatialDataReferenceId
  ) {
    return Response.ok(
      spatialDataReferenceService.getReferencePayload(collectionId, dataObjectId, spatialDataReferenceId)
    ).build();
  }
}
