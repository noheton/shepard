package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Response;

public interface CollectionRest {

	@Tag(name = Constants.COLLECTION)
	@Operation(description = "Get all collections")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CollectionIO.class))))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getAllCollections(String name, Integer page, Integer size, DataObjectAttributes orderAttribute,
			Boolean orderDesc);

	@Tag(name = Constants.COLLECTION)
	@Operation(description = "Get collection")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getCollection(long collectionId);

	@Tag(name = Constants.COLLECTION)
	@Operation(description = "Create a new collection")
	@ApiResponse(description = "created", responseCode = "201", content = @Content(schema = @Schema(implementation = CollectionIO.class)))
	Response createCollection(
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = CollectionIO.class))) @Valid CollectionIO collection);

	@Tag(name = Constants.COLLECTION)
	@Operation(description = "Update collection")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response updateCollection(long collectionId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = CollectionIO.class))) @Valid CollectionIO collection);

	@Tag(name = Constants.COLLECTION)
	@Operation(description = "Delete collection")
	@ApiResponse(description = "deleted", responseCode = "204")
	@ApiResponse(description = "not found", responseCode = "404")
	Response deleteCollection(long collectionId);

	@Tag(name = Constants.COLLECTION)
	@Operation(description = "Get permissions")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = PermissionsIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response getPermissions(long collectionId);

	@Tag(name = Constants.COLLECTION)
	@Operation(description = "Edit permissions")
	@ApiResponse(description = "ok", responseCode = "200", content = @Content(schema = @Schema(implementation = PermissionsIO.class)))
	@ApiResponse(description = "not found", responseCode = "404")
	Response editPermissions(long collectionId,
			@RequestBody(required = true, content = @Content(schema = @Schema(implementation = PermissionsIO.class))) @Valid PermissionsIO permissions);

}
