package de.dlr.shepard.context.references.uri.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
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
  Constants.URI_REFERENCES
)
@RequestScoped
public class URIReferenceRest {

  private URIReferenceService uriReferenceService;

  @Context
  private SecurityContext securityContext;

  URIReferenceRest() {}

  @Inject
  public URIReferenceRest(URIReferenceService uriReferenceService) {
    this.uriReferenceService = uriReferenceService;
  }

  @GET
  @Tag(name = Constants.URI_REFERENCE)
  @Operation(description = "Get all uri references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = URIReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getAllUriReferences(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    var references = uriReferenceService.getAllReferencesByDataObjectShepardId(dataObjectId, versionUID);
    var result = new ArrayList<URIReferenceIO>(references.size());
    for (var ref : references) {
      result.add(new URIReferenceIO(ref));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.URI_REFERENCE_ID + "}")
  @Tag(name = Constants.URI_REFERENCE)
  @Operation(description = "Get uri reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = URIReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.URI_REFERENCE_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getUriReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.URI_REFERENCE_ID) long referenceId,
    @QueryParam(Constants.VERSION_UID) UUID versionUID
  ) {
    var reference = uriReferenceService.getReferenceByShepardId(referenceId, versionUID);
    return Response.ok(new URIReferenceIO(reference)).build();
  }

  @POST
  @Subscribable
  @Tag(name = Constants.URI_REFERENCE)
  @Operation(description = "Create a new uri reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = URIReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response createUriReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = URIReferenceIO.class))
    ) @Valid URIReferenceIO timeseriesReference
  ) {
    var result = uriReferenceService.createReferenceByShepardId(
      dataObjectId,
      timeseriesReference,
      securityContext.getUserPrincipal().getName()
    );

    return Response.ok(new URIReferenceIO(result)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.URI_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.URI_REFERENCE)
  @Operation(description = "Delete uri reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.URI_REFERENCE_ID)
  public Response deleteUriReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.URI_REFERENCE_ID) long referenceId
  ) {
    return uriReferenceService.deleteReferenceByShepardId(referenceId, securityContext.getUserPrincipal().getName())
      ? Response.status(Status.NO_CONTENT).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }
}
