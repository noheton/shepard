package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.neo4Core.services.FileReferenceService;
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
  Constants.FILE_REFERENCES
)
public class FileReferenceRestImpl implements FileReferenceRest {

  private FileReferenceService fileReferenceService = new FileReferenceService();

  @Context
  private SecurityContext securityContext;

  @GET
  @Override
  public Response getAllFileReferences(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId
  ) {
    var references = fileReferenceService.getAllReferencesByDataObjectShepardId(dataObjectId);
    var result = new ArrayList<FileReferenceIO>(references.size());
    for (var ref : references) {
      result.add(new FileReferenceIO(ref));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.FILE_REFERENCE_ID + "}")
  @Override
  public Response getFileReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.FILE_REFERENCE_ID) long referenceId
  ) {
    var ref = fileReferenceService.getReferenceByShepardId(referenceId);
    return Response.ok(new FileReferenceIO(ref)).build();
  }

  @POST
  @Subscribable
  @Override
  public Response createFileReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    FileReferenceIO fileReference
  ) {
    var ref = fileReferenceService.createReferenceByShepardId(
      dataObjectId,
      fileReference,
      securityContext.getUserPrincipal().getName()
    );
    return Response.ok(new FileReferenceIO(ref)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.FILE_REFERENCE_ID + "}")
  @Subscribable
  @Override
  public Response deleteFileReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.FILE_REFERENCE_ID) long fileReferenceId
  ) {
    var result = fileReferenceService.deleteReferenceByShepardId(
      fileReferenceId,
      securityContext.getUserPrincipal().getName()
    );
    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.FILE_REFERENCE_ID + "}/payload/{" + Constants.OID + "}")
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
  @Override
  public Response getFilePayload(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.FILE_REFERENCE_ID) long fileReferenceId,
    @PathParam(Constants.OID) String oid
  ) {
    var payload = fileReferenceService.getPayloadByShepardId(
      fileReferenceId,
      oid,
      securityContext.getUserPrincipal().getName()
    );
    return payload != null
      ? Response.ok(payload.getInputStream(), MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=\"" + payload.getName() + "\"")
        .header("Content-Length", payload.getSize())
        .build()
      : Response.status(Status.NOT_FOUND).build();
  }

  @GET
  @Path("/{" + Constants.FILE_REFERENCE_ID + "}/payload")
  @Override
  public Response getFiles(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
    @PathParam(Constants.FILE_REFERENCE_ID) long fileId
  ) {
    var ret = fileReferenceService.getFilesByShepardId(fileId);
    return Response.ok(ret).build();
  }
}
