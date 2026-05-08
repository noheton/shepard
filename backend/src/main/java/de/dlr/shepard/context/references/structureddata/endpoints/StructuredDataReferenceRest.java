package de.dlr.shepard.context.references.structureddata.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.structureddata.io.StructuredDataReferenceIO;
import de.dlr.shepard.context.references.structureddata.services.StructuredDataReferenceService;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
  Constants.STRUCTURED_DATA_REFERENCES
)
@RequestScoped
public class StructuredDataReferenceRest {

  @Inject
  StructuredDataReferenceService structuredDataReferenceService;

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.STRUCTURED_DATA_REFERENCE)
  @Operation(description = "Get all structuredData references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StructuredDataReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getAllStructuredDataReferences(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }
    var references = structuredDataReferenceService.getAllReferencesByDataObjectId(
      collectionId,
      dataObjectId,
      versionUUID
    );
    List<StructuredDataReferenceIO> result = new ArrayList<StructuredDataReferenceIO>(references.size());
    for (var ref : references) {
      result.add(new StructuredDataReferenceIO(ref));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTURED_DATA_REFERENCE_ID + "}")
  @Tag(name = Constants.STRUCTURED_DATA_REFERENCE)
  @Operation(description = "Get structuredData reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.STRUCTURED_DATA_REFERENCE_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getStructuredDataReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.STRUCTURED_DATA_REFERENCE_ID) @NotNull @PositiveOrZero Long referenceId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }
    StructuredDataReference ref = structuredDataReferenceService.getReference(
      collectionId,
      dataObjectId,
      referenceId,
      versionUUID
    );
    return Response.ok(new StructuredDataReferenceIO(ref)).build();
  }

  @POST
  @Subscribable
  @Tag(name = Constants.STRUCTURED_DATA_REFERENCE)
  @Operation(description = "Create a new structuredData reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response createStructuredDataReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class))
    ) @Valid StructuredDataReferenceIO structuredDataReference
  ) {
    StructuredDataReference ref = structuredDataReferenceService.createReference(
      collectionId,
      dataObjectId,
      structuredDataReference
    );
    return Response.ok(new StructuredDataReferenceIO(ref)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.STRUCTURED_DATA_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.STRUCTURED_DATA_REFERENCE)
  @Operation(description = "Delete structuredData reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.STRUCTURED_DATA_REFERENCE_ID)
  public Response deleteStructuredDataReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.STRUCTURED_DATA_REFERENCE_ID) @NotNull @PositiveOrZero Long structuredDataReferenceId
  ) {
    structuredDataReferenceService.deleteReference(collectionId, dataObjectId, structuredDataReferenceId);
    return Response.status(Status.NO_CONTENT).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTURED_DATA_REFERENCE_ID + "}/payload")
  @Tag(name = Constants.STRUCTURED_DATA_REFERENCE)
  @Operation(description = "Get structured data payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StructuredDataPayload.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.STRUCTURED_DATA_REFERENCE_ID)
  public Response getStructuredDataPayload(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.STRUCTURED_DATA_REFERENCE_ID) @NotNull @PositiveOrZero Long structuredDataId
  ) {
    List<StructuredDataPayload> payload = structuredDataReferenceService.getAllPayloads(
      collectionId,
      dataObjectId,
      structuredDataId
    );
    return Response.ok(payload).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTURED_DATA_REFERENCE_ID + "}/payload/{" + Constants.OID + "}")
  @Tag(name = Constants.STRUCTURED_DATA_REFERENCE)
  @Operation(description = "Get a specific structured data payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataPayload.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.STRUCTURED_DATA_CONTAINER_ID)
  @Parameter(name = Constants.OID)
  public Response getSpecificStructuredDataPayload(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.STRUCTURED_DATA_REFERENCE_ID) @NotNull @PositiveOrZero Long structuredDataId,
    @PathParam(Constants.OID) @NotBlank String oid
  ) {
    StructuredDataPayload payload = structuredDataReferenceService.getPayload(
      collectionId,
      dataObjectId,
      structuredDataId,
      oid
    );
    return Response.ok(payload).build();
  }
}
