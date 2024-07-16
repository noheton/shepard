package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import de.dlr.shepard.neo4Core.services.URIReferenceService;
import de.dlr.shepard.util.Constants;
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
  Constants.URI_REFERENCES
)
public class URIReferenceRestImpl implements URIReferenceRest {

  private URIReferenceService uriReferenceService = new URIReferenceService();

  @Context
  private SecurityContext securityContext;

  @GET
  @Override
  public Response getAllUriReferences(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId
  ) {
    var references = uriReferenceService.getAllReferencesByDataObjectShepardId(dataObjectId);
    var result = new ArrayList<URIReferenceIO>(references.size());
    for (var ref : references) {
      result.add(new URIReferenceIO(ref));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.URI_REFERENCE_ID + "}")
  @Override
  public Response getUriReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.URI_REFERENCE_ID) long referenceId
  ) {
    var reference = uriReferenceService.getReferenceByShepardId(referenceId);
    return Response.ok(new URIReferenceIO(reference)).build();
  }

  @POST
  @Subscribable
  @Override
  public Response createUriReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    URIReferenceIO timeseriesReference
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
  @Override
  public Response deleteUriReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.URI_REFERENCE_ID) long referenceId
  ) {
    return uriReferenceService.deleteReferenceByShepardId(referenceId, securityContext.getUserPrincipal().getName())
      ? Response.status(Status.NO_CONTENT).build()
      : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }
}
