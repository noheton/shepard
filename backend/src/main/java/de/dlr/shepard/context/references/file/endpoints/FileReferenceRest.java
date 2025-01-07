package de.dlr.shepard.context.references.file.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.references.file.services.FileReferenceService;
import de.dlr.shepard.data.file.entities.ShepardFile;
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
  Constants.DATA_OBJECTS +
  "/{" +
  Constants.DATA_OBJECT_ID +
  "}/" +
  Constants.FILE_REFERENCES
)
@RequestScoped
public class FileReferenceRest {

  private FileReferenceService fileReferenceService;

  @Context
  private SecurityContext securityContext;

  FileReferenceRest() {}

  @Inject
  public FileReferenceRest(FileReferenceService fileReferenceService) {
    this.fileReferenceService = fileReferenceService;
  }

  @GET
  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Get all file references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = FileReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response getAllFileReferences(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId
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
  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Get file reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = FileReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.FILE_REFERENCE_ID)
  public Response getFileReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.FILE_REFERENCE_ID) long referenceId
  ) {
    var ref = fileReferenceService.getReferenceByShepardId(referenceId);
    return Response.ok(new FileReferenceIO(ref)).build();
  }

  @POST
  @Subscribable
  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Create a new file reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = FileReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response createFileReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = FileReferenceIO.class))
    ) @Valid FileReferenceIO fileReference
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
  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Delete file reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.FILE_REFERENCE_ID)
  public Response deleteFileReference(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
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
  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Get file payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.FILE_REFERENCE_ID)
  @Parameter(name = Constants.OID)
  public Response getFilePayload(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
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
  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Get associated files")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ShepardFile.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.FILE_REFERENCE_ID)
  public Response getFiles(
    @PathParam(Constants.COLLECTION_ID) long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) long dataObjectId,
    @PathParam(Constants.FILE_REFERENCE_ID) long fileId
  ) {
    var ret = fileReferenceService.getFilesByShepardId(fileId);
    return Response.ok(ret).build();
  }
}
