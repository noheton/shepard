package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import de.dlr.shepard.neo4Core.services.DataObjectReferenceService;
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
  Constants.DATAOBJECT_REFERENCES
)
@RequestScoped
public class DataObjectReferenceRest {

  private DataObjectReferenceService dataObjectReferenceService;

  @Context
  private SecurityContext securityContext;

  DataObjectReferenceRest() {}

  @Inject
  public DataObjectReferenceRest(DataObjectReferenceService dataObjectReferenceService) {
    this.dataObjectReferenceService = dataObjectReferenceService;
  }

  @GET
  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Get all dataObject references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = DataObjectReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  public Response getAllDataObjectReferences(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId
  ) {
    var references = dataObjectReferenceService.getAllReferencesByDataObjectShepardId(dataObjectId);
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
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  @Parameter(name = Constants.DATAOBJECT_REFERENCE_ID)
  public Response getDataObjectReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.DATAOBJECT_REFERENCE_ID) long dataObjectReferenceId
  ) {
    var result = dataObjectReferenceService.getReferenceByShepardId(dataObjectReferenceId);
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
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  public Response createDataObjectReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class))
    ) @Valid DataObjectReferenceIO dataObjectReference
  ) {
    var result = dataObjectReferenceService.createReferenceByShepardId(
      dataObjectId,
      dataObjectReference,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new DataObjectReferenceIO(result)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.DATAOBJECT_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Delete dataObject reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  @Parameter(name = Constants.DATAOBJECT_REFERENCE_ID)
  public Response deleteDataObjectReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.DATAOBJECT_REFERENCE_ID) long dataObjectReferenceId
  ) {
    var result = dataObjectReferenceService.deleteReferenceByShepardId(
      dataObjectReferenceId,
      securityContext.getUserPrincipal().getName()
    );
    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATAOBJECT_ID)
  @Parameter(name = Constants.DATAOBJECT_REFERENCE_ID)
  public Response getDataObjectReferencePayload(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.DATAOBJECT_REFERENCE_ID) long dataObjectReferenceId
  ) {
    var payload = dataObjectReferenceService.getPayloadByShepardId(dataObjectReferenceId);
    return payload != null ? Response.ok(new DataObjectIO(payload)).build() : Response.status(Status.NOT_FOUND).build();
  }
}
