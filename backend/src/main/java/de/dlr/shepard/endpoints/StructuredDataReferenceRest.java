package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.neo4Core.services.StructuredDataReferenceService;
import de.dlr.shepard.util.Constants;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
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
  Constants.DATAOBJECTS +
  "/{" +
  Constants.DATAOBJECT_ID +
  "}/" +
  Constants.STRUCTUREDDATA_REFERENCES
)
@RequestScoped
public class StructuredDataReferenceRest {

  private StructuredDataReferenceService structuredDataReferenceService;

  @Context
  private SecurityContext securityContext;

  StructuredDataReferenceRest() {}

  @Inject
  public StructuredDataReferenceRest(StructuredDataReferenceService structuredDataReferenceService) {
    this.structuredDataReferenceService = structuredDataReferenceService;
  }

  @GET
  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Get all structureddata references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StructuredDataReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  public Response getAllStructuredDataReferences(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId
  ) {
    var references = structuredDataReferenceService.getAllReferencesByDataObjectShepardId(dataObjectId);
    var result = new ArrayList<StructuredDataReferenceIO>(references.size());
    for (var ref : references) {
      result.add(new StructuredDataReferenceIO(ref));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTUREDDATA_REFERENCE_ID + "}")
  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Get structureddata reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  @Parameter(name = Constants.STRUCTUREDDATA_REFERENCE_ID)
  public Response getStructuredDataReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.STRUCTUREDDATA_REFERENCE_ID) long referenceId
  ) {
    var ref = structuredDataReferenceService.getReferenceByShepardId(referenceId);
    return Response.ok(new StructuredDataReferenceIO(ref)).build();
  }

  @POST
  @Subscribable
  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Create a new structureddata reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  public Response createStructuredDataReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class))
    ) @Valid StructuredDataReferenceIO structuredDataReference
  ) {
    var ref = structuredDataReferenceService.createReferenceByShepardId(
      dataObjectId,
      structuredDataReference,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new StructuredDataReferenceIO(ref)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.STRUCTUREDDATA_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Delete structureddata reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  @Parameter(name = Constants.STRUCTUREDDATA_REFERENCE_ID)
  public Response deleteStructuredDataReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.STRUCTUREDDATA_REFERENCE_ID) long structuredDataReferenceId
  ) {
    var result = structuredDataReferenceService.deleteReferenceByShepardId(
      structuredDataReferenceId,
      securityContext.getUserPrincipal().getName()
    );
    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTUREDDATA_REFERENCE_ID + "}/payload")
  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Get structured data payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StructuredDataPayload.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  @Parameter(name = Constants.STRUCTUREDDATA_REFERENCE_ID)
  public Response getStructuredDataPayload(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.STRUCTUREDDATA_REFERENCE_ID) long structuredDataId
  ) {
    var payload = structuredDataReferenceService.getAllPayloadsByShepardId(
      structuredDataId,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(payload).build();
  }

  @GET
  @Path("/{" + Constants.STRUCTUREDDATA_REFERENCE_ID + "}/payload/{" + Constants.OID + "}")
  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Get a specific structured data payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataPayload.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  @Parameter(name = Constants.STRUCTUREDDATA_CONTAINER_ID)
  @Parameter(name = Constants.OID)
  public Response getSpecificStructuredDataPayload(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.STRUCTUREDDATA_REFERENCE_ID) long structuredDataId,
    @PathParam(Constants.OID) String oid
  ) {
    var payload = structuredDataReferenceService.getPayloadByShepardId(
      structuredDataId,
      oid,
      securityContext.getUserPrincipal().getName()
    );
    return payload != null ? Response.ok(payload).build() : Response.status(Status.NOT_FOUND).build();
  }
}
