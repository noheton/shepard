package de.dlr.shepard.endpoints;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

public interface CollectionRest {

	@GET
	@Tag(name = Constants.COLLECTION)
	@Operation(description = "Get all collections")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CollectionIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllCollections(@QueryParam(Constants.QP_NAME) String name, @QueryParam(Constants.QP_PAGE) Integer page,
			@QueryParam(Constants.QP_SIZE) Integer size);

	@GET
	@Path("/{" + Constants.COLLECTION_ID + "}")
	@Tag(name = Constants.COLLECTION)
	@Operation(description = "Get collection")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getCollection(@PathParam(Constants.COLLECTION_ID) long collectionId);

	@POST
	@Tag(name = Constants.COLLECTION)
	@Operation(description = "Create a new collection")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = CollectionIO.class)))
	Response createCollection(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = CollectionIO.class))) CollectionIO collection);

	@PUT
	@Tag(name = Constants.COLLECTION)
	@Path("/{" + Constants.COLLECTION_ID + "}")
	@Operation(description = "Update collection")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response updateCollection(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = CollectionIO.class))) CollectionIO collection);

	@DELETE
	@Path("/{" + Constants.COLLECTION_ID + "}")
	@Tag(name = Constants.COLLECTION)
	@Operation(description = "Delete collection")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteCollection(@PathParam(Constants.COLLECTION_ID) long collectionId);

}
