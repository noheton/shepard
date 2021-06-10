package de.dlr.shepard.endpoints;

import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import de.dlr.shepard.mongoDB.File;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

public interface FileRest {

	@GET
	@Tag(name = Constants.FILE)
	@Operation(description = "Get all file containers")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = FileContainerIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllFileContainers();

	@GET
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}")
	@Tag(name = Constants.FILE)
	@Operation(description = "Get file container")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = FileContainerIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getFileContainer(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId);

	@POST
	@Tag(name = Constants.FILE)
	@Operation(description = "Create a new file container")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = FileContainerIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createFileContainer(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = FileContainerIO.class))) FileContainerIO fileContainer);

	@DELETE
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}")
	@Tag(name = Constants.FILE)
	@Operation(description = "Delete file container")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteFileContainer(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId);

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload")
	@Tag(name = Constants.FILE)
	@Operation(description = "Upload a new file")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = File.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response createFile(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
			@Parameter(schema = @Schema(type = "string", format = "binary", description = "File which you want to upload")) @FormDataParam(Constants.FILE) InputStream fileInputStream,
			@Parameter(hidden = true) @FormDataParam(Constants.FILE) FormDataContentDisposition fileMetaData);

}
