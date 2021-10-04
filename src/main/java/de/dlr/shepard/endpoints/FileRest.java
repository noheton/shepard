package de.dlr.shepard.endpoints;

import java.io.InputStream;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import de.dlr.shepard.mongoDB.File;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public interface FileRest {

	@Tag(name = Constants.FILE)
	@Operation(description = "Get all file containers")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = FileContainerIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllFileContainers(String name, Integer page, Integer size, ContainerAttributes orderAttribute,
			Boolean orderDesc);

	@Tag(name = Constants.FILE)
	@Operation(description = "Get file container")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = FileContainerIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getFileContainer(long fileContainerId);

	@Tag(name = Constants.FILE)
	@Operation(description = "Create a new file container")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = FileContainerIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createFileContainer(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = FileContainerIO.class))) @Valid FileContainerIO fileContainer);

	@Tag(name = Constants.FILE)
	@Operation(description = "Delete file container")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteFileContainer(long fileContainerId);

	@Tag(name = Constants.FILE)
	@Operation(description = "Get files")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = File.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllFiles(long fileContainerId);

	@Tag(name = Constants.FILE)
	@Operation(description = "Get file")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getFile(long fileContainerId, String oid);

	@Tag(name = Constants.FILE)
	@Operation(description = "Delete file")
	@ApiResponse(description = "ok", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteFile(long fileContainerId, String oid);

	@Tag(name = Constants.FILE)
	@Operation(description = "Upload a new file")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = File.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createFile(long fileContainerId,
			@Parameter(required = true, schema = @Schema(type = "string", format = "binary", description = "File which you want to upload")) InputStream fileInputStream,
			@Parameter(hidden = true) FormDataContentDisposition fileMetaData);

}
