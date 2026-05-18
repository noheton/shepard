package de.dlr.shepard.context.references.uri.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.uri.io.URIReferenceIO;
import de.dlr.shepard.context.references.uri.services.URIReferenceService;
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
  Constants.URI_REFERENCES
)
@RequestScoped
public class URIReferenceRest {

  @Inject
  URIReferenceService uriReferenceService;

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.URI_REFERENCE)
  @Operation(
    summary = "List URIReferences on a DataObject.",
    description =
      "Returns the URIReferences attached to the DataObject. A URIReference is " +
      "a lightweight string-shaped pointer to an external resource (publication " +
      "DOI, dataset URL, …) — no payload bytes, only metadata. Optional " +
      "'versionUID' returns references at a specific Version snapshot. Requires " +
      "Read on the parent DataObject."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = URIReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getAllUriReferences(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }
    var references = uriReferenceService.getAllReferencesByDataObjectId(collectionId, dataObjectId, versionUUID);
    var result = new ArrayList<URIReferenceIO>(references.size());
    for (var ref : references) {
      result.add(new URIReferenceIO(ref));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.URI_REFERENCE_ID + "}")
  @Tag(name = Constants.URI_REFERENCE)
  @Operation(
    summary = "Get a single URIReference.",
    description =
      "Returns one URIReference identified by 'referenceId' on the given " +
      "DataObject. Optional 'versionUID' selects a specific Version snapshot. " +
      "Requires Read on the parent DataObject."
  )
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = URIReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.URI_REFERENCE_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getUriReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.URI_REFERENCE_ID) @NotNull @PositiveOrZero Long referenceId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }
    var reference = uriReferenceService.getReference(collectionId, dataObjectId, referenceId, versionUUID);
    return Response.ok(new URIReferenceIO(reference)).build();
  }

  @POST
  @Subscribable
  @Tag(name = Constants.URI_REFERENCE)
  @Operation(
    summary = "Create a URIReference on a DataObject.",
    description =
      "Attaches a new URIReference to the given DataObject. Body fields: " +
      "'name' (required), 'description', 'uri' (the external URL/IRI being " +
      "referenced; required and validated). Example: " +
      "{\"name\":\"Project DOI\", \"uri\":\"https://doi.org/10.5072/abc\"}. " +
      "Requires Write on the parent DataObject."
  )
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = URIReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response createUriReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = URIReferenceIO.class))
    ) @Valid URIReferenceIO timeseriesReference
  ) {
    var result = uriReferenceService.createReference(collectionId, dataObjectId, timeseriesReference);
    return Response.ok(new URIReferenceIO(result)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.URI_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.URI_REFERENCE)
  @Operation(
    summary = "Delete a URIReference.",
    description =
      "Soft-deletes the URIReference identified by 'referenceId'. Returns 204 " +
      "when the reference is removed (or was already gone). Requires Write " +
      "permission on the parent DataObject."
  )
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.URI_REFERENCE_ID)
  public Response deleteUriReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.URI_REFERENCE_ID) @NotNull @PositiveOrZero Long referenceId
  ) {
    uriReferenceService.deleteReference(collectionId, dataObjectId, referenceId);
    return Response.status(Status.NO_CONTENT).build();
  }
}
