package de.dlr.shepard.endpoints;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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

public interface FileReferenceRest {

	@GET
	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Get all file references")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = FileReferenceIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllFileReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId);

	@GET
	@Path("/{" + Constants.FILE_REFERENCE_ID + "}")
	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Get file reference")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = FileReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getFileReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.FILE_REFERENCE_ID) long referenceId);

	@POST
	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Create a new file reference")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = FileReferenceIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createFileReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = FileReferenceIO.class))) FileReferenceIO fileReference)
			throws InvalidBodyException;

	@DELETE
	@Path("/{" + Constants.FILE_REFERENCE_ID + "}")
	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Delete file reference")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteBasicReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.FILE_REFERENCE_ID) long fileReferenceId);

	@GET
	@Path("/{" + Constants.FILE_REFERENCE_ID + "}/payload/{" + Constants.OID + "}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Get file payload")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getFilePayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.FILE_REFERENCE_ID) long fileReferenceId, @PathParam(Constants.OID) String oid);

	@GET
	@Path("/{" + Constants.FILE_REFERENCE_ID + "}/payload")
	@Tag(name = Constants.FILE_REFERENCE)
	@Operation(description = "Get associated files")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = File.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllFiles(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, @PathParam(Constants.FILE_REFERENCE_ID) long fileId);

}
