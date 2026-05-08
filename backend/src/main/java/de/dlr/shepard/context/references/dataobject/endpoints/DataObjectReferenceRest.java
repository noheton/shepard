package de.dlr.shepard.context.references.dataobject.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import de.dlr.shepard.context.references.dataobject.io.DataObjectReferenceIO;
import de.dlr.shepard.context.references.dataobject.services.DataObjectReferenceService;
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
import jakarta.ws.rs.core.Response.Status;
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
  Constants.DATAOBJECT_REFERENCES
)
@RequestScoped
public class DataObjectReferenceRest {

  @Inject
  DataObjectReferenceService dataObjectReferenceService;

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Get all dataObject references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = DataObjectReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getAllDataObjectReferences(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }
    List<DataObjectReference> references = dataObjectReferenceService.getAllReferencesByDataObjectId(
      collectionId,
      dataObjectId,
      versionUUID
    );
    var result = new ArrayList<DataObjectReferenceIO>(references.size());
    for (var reference : references) {
      result.add(new DataObjectReferenceIO(reference));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.DATAOBJECT_REFERENCE_ID + "}")
  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Get dataObject reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.DATAOBJECT_REFERENCE_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getDataObjectReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.DATAOBJECT_REFERENCE_ID) @NotNull @PositiveOrZero Long dataObjectReferenceId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }
    DataObjectReference result = dataObjectReferenceService.getReference(
      collectionId,
      dataObjectId,
      dataObjectReferenceId,
      versionUUID
    );
    return Response.ok(new DataObjectReferenceIO(result)).build();
  }

  @POST
  @Subscribable
  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Create a new dataObject reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response createDataObjectReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class))
    ) @Valid DataObjectReferenceIO dataObjectReference
  ) {
    DataObjectReference result = dataObjectReferenceService.createReference(
      collectionId,
      dataObjectId,
      dataObjectReference
    );
    return Response.ok(new DataObjectReferenceIO(result)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.DATAOBJECT_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Delete dataObject reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.DATAOBJECT_REFERENCE_ID)
  public Response deleteDataObjectReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.DATAOBJECT_REFERENCE_ID) @NotNull @PositiveOrZero Long dataObjectReferenceId
  ) {
    dataObjectReferenceService.deleteReference(collectionId, dataObjectId, dataObjectReferenceId);
    return Response.status(Status.NO_CONTENT).build();
  }

  @GET
  @Path("/{" + Constants.DATAOBJECT_REFERENCE_ID + "}/payload")
  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Get dataObject reference payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.DATAOBJECT_REFERENCE_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getDataObjectReferencePayload(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.DATAOBJECT_REFERENCE_ID) @NotNull @PositiveOrZero Long dataObjectReferenceId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }
    DataObject payload = dataObjectReferenceService.getPayload(
      collectionId,
      dataObjectId,
      dataObjectReferenceId,
      versionUUID
    );
    return Response.ok(new DataObjectIO(payload)).build();
  }
}
