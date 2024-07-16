package de.dlr.shepard.endpoints;

import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

public interface FileReferenceRest {
  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Get all file references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = FileReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllFileReferences(long collectionId, long dataObjectId);

  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Get file reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = FileReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getFileReference(long collectionId, long dataObjectId, long referenceId);

  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Create a new file reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = FileReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createFileReference(
    long collectionId,
    long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = FileReferenceIO.class))
    ) @Valid FileReferenceIO fileReference
  );

  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Delete file reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteFileReference(long collectionId, long dataObjectId, long fileReferenceId);

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
  Response getFilePayload(long collectionId, long dataObjectId, long fileReferenceId, String oid);

  @Tag(name = Constants.FILE_REFERENCE)
  @Operation(description = "Get associated files")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ShepardFile.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getFiles(long collectionId, long dataObjectId, long fileId);
}
