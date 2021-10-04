package de.dlr.shepard.endpoints;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.mongoDB.File;
import de.dlr.shepard.neo4Core.io.FileReferenceIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public interface FileReferenceRest {

	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Get all file references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = FileReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllFileReferences(long collectionId, long dataObjectId);

	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Get file reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = FileReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getFileReference(long collectionId, long dataObjectId, long referenceId);

	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Create a new file reference")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = FileReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createFileReference(long collectionId, long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = FileReferenceIO.class))) @Valid FileReferenceIO fileReference)
			throws InvalidBodyException;

	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Delete file reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteFileReference(long collectionId, long dataObjectId, long fileReferenceId);

	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Get file payload")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getFilePayload(long collectionId, long dataObjectId, long fileReferenceId, String oid);

	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Get associated files")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = File.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getFiles(long collectionId, long dataObjectId, long fileId);

}
