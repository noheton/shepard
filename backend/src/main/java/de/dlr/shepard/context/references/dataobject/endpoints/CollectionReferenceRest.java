package de.dlr.shepard.context.references.dataobject.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.collection.io.CollectionIO;
import de.dlr.shepard.context.references.dataobject.io.CollectionReferenceIO;
import de.dlr.shepard.context.references.dataobject.services.CollectionReferenceService;
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
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
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
  Constants.COLLECTION_REFERENCES
)
@RequestScoped
public class CollectionReferenceRest {

  private CollectionReferenceService collectionReferenceService;

  @Context
  private SecurityContext securityContext;

  CollectionReferenceRest() {}

  @Inject
  public CollectionReferenceRest(CollectionReferenceService collectionReferenceService) {
    this.collectionReferenceService = collectionReferenceService;
  }

  @GET
  @Tag(name = Constants.COLLECTION_REFERENCE)
  @Operation(description = "Get all collection references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = CollectionReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getAllCollectionReferences(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    var references = collectionReferenceService.getAllReferencesByDataObjectShepardId(dataObjectId, versionUID);
    var result = new ArrayList<CollectionReferenceIO>(references.size());
    for (var reference : references) {
      result.add(new CollectionReferenceIO(reference));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.COLLECTION_REFERENCE_ID + "}")
  @Tag(name = Constants.COLLECTION_REFERENCE)
  @Operation(description = "Get collection reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CollectionReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.COLLECTION_REFERENCE_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getCollectionReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.COLLECTION_REFERENCE_ID) long collectionReferenceId,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    var result = collectionReferenceService.getReferenceByShepardId(collectionReferenceId, versionUID);
    return Response.ok(new CollectionReferenceIO(result)).build();
  }

  @POST
  @Subscribable
  @Tag(name = Constants.COLLECTION_REFERENCE)
  @Operation(description = "Create a new collection reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = CollectionReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response createCollectionReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CollectionReferenceIO.class))
    ) @Valid CollectionReferenceIO collectionReference
  ) {
    var result = collectionReferenceService.createReferenceByShepardId(
      dataObjectId,
      collectionReference,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new CollectionReferenceIO(result)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.COLLECTION_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.COLLECTION_REFERENCE)
  @Operation(description = "Delete collection reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.COLLECTION_REFERENCE_ID)
  public Response deleteCollectionReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.COLLECTION_REFERENCE_ID) long collectionReferenceId
  ) {
    var result = collectionReferenceService.deleteReferenceByShepardId(
      collectionReferenceId,
      securityContext.getUserPrincipal().getName()
    );
    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.COLLECTION_REFERENCE_ID + "}/payload")
  @Tag(name = Constants.COLLECTION_REFERENCE)
  @Operation(description = "Get collection reference payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CollectionIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.COLLECTION_REFERENCE_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getCollectionReferencePayload(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.COLLECTION_REFERENCE_ID) long collectionReferenceId,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    var payload = collectionReferenceService.getPayloadByShepardId(collectionReferenceId, versionUID);
    return Response.ok(new CollectionIO(payload)).build();
  }
}
