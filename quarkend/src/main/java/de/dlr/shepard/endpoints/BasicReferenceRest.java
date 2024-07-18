package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.neo4Core.orderBy.BasicReferenceAttributes;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

public interface BasicReferenceRest {
  @Tag(name = Constants.BASIC_REFERENCE)
  @Operation(description = "Get all references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = BasicReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
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
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = BasicReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getBasicReference(long collectionId, long dataObjectId, long referenceId);

  @Tag(name = Constants.BASIC_REFERENCE)
  @Operation(description = "Delete reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteBasicReference(long collectionId, long dataObjectId, long basicReferenceId);
}
