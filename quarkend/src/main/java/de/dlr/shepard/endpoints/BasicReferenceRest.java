package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.neo4Core.orderBy.BasicReferenceAttributes;
import de.dlr.shepard.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.core.Response;

public interface BasicReferenceRest {
  @Tag(name = Constants.BASIC_REFERENCE)
  @Operation(description = "Get all references")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(array = @ArraySchema(schema = @Schema(implementation = BasicReferenceIO.class)))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response getAllReferences(
    long collectionId,
    long dataObjectId,
    String name,
    Integer page,
    Integer size,
    BasicReferenceAttributes orderAttribute,
    Boolean orderDesc
  );

  @Tag(name = Constants.BASIC_REFERENCE)
  @Operation(description = "Get reference")
  @ApiResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = BasicReferenceIO.class))
  )
  @ApiResponse(description = "not found", responseCode = "404")
  Response getBasicReference(long collectionId, long dataObjectId, long referenceId);

  @Tag(name = Constants.BASIC_REFERENCE)
  @Operation(description = "Delete reference")
  @ApiResponse(description = "deleted", responseCode = "204")
  @ApiResponse(description = "not found", responseCode = "404")
  Response deleteBasicReference(long collectionId, long dataObjectId, long basicReferenceId);
}
