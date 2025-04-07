package de.dlr.shepard.context.references.file.endpoints;

import de.dlr.shepard.common.filters.Subscribable;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.io.FileReferenceIO;
import de.dlr.shepard.context.references.file.services.FileReferenceService;
import de.dlr.shepard.data.file.entities.ShepardFile;
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

  @Inject
  FileReferenceService fileReferenceService;

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Get all file references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = FileReferenceIO.class))
  )
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getAllFileReferences(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }
    List<FileReference> references = fileReferenceService.getAllReferencesByDataObjectId(
      collectionId,
      dataObjectId,
      versionUUID
    );
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.FILE_REFERENCE_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getFileReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.FILE_REFERENCE_ID) @NotNull @PositiveOrZero Long referenceId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }
    FileReference ref = fileReferenceService.getReference(collectionId, dataObjectId, referenceId, versionUUID);
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  public Response createFileReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = FileReferenceIO.class))
    ) @Valid FileReferenceIO fileReference
  ) {
    FileReference ref = fileReferenceService.createReference(collectionId, dataObjectId, fileReference);
    return Response.ok(new FileReferenceIO(ref)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.FILE_REFERENCE_ID + "}")
  @Subscribable
  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Delete file reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.FILE_REFERENCE_ID)
  public Response deleteFileReference(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.FILE_REFERENCE_ID) @NotNull @PositiveOrZero Long fileReferenceId
  ) {
    fileReferenceService.deleteReference(collectionId, dataObjectId, fileReferenceId);
    return Response.status(Status.NO_CONTENT).build();
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.FILE_REFERENCE_ID)
  @Parameter(name = Constants.OID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getFilePayload(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.FILE_REFERENCE_ID) @NotNull @PositiveOrZero Long fileReferenceId,
    @PathParam(Constants.OID) @NotNull String oid,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }
    NamedInputStream payload = fileReferenceService.getPayload(
      collectionId,
      dataObjectId,
      fileReferenceId,
      oid,
      versionUUID
    );
    return Response.ok(payload.getInputStream(), MediaType.APPLICATION_OCTET_STREAM)
      .header("Content-Disposition", "attachment; filename=\"" + payload.getName() + "\"")
      .header("Content-Length", payload.getSize())
      .build();
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
  @APIResponse(responseCode = "400", description = "bad request")
  @APIResponse(responseCode = "401", description = "not authorized")
  @APIResponse(responseCode = "403", description = "forbidden")
  @APIResponse(responseCode = "404", description = "not found")
  @Parameter(name = Constants.COLLECTION_ID)
  @Parameter(name = Constants.DATA_OBJECT_ID)
  @Parameter(name = Constants.FILE_REFERENCE_ID)
  @Parameter(name = Constants.VERSION_UID)
  public Response getFiles(
    @PathParam(Constants.COLLECTION_ID) @NotNull @PositiveOrZero Long collectionId,
    @PathParam(Constants.DATA_OBJECT_ID) @NotNull @PositiveOrZero Long dataObjectId,
    @PathParam(Constants.FILE_REFERENCE_ID) @NotNull @PositiveOrZero Long fileId,
    @QueryParam(Constants.VERSION_UID) @org.hibernate.validator.constraints.UUID String versionUID
  ) {
    UUID versionUUID = null;
    if (versionUID != null) {
      versionUUID = UUID.fromString(versionUID);
    }
    var ret = fileReferenceService.getFiles(collectionId, dataObjectId, fileId, versionUUID);
    return Response.ok(ret).build();
  }
}
