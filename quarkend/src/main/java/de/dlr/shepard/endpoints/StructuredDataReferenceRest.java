package de.dlr.shepard.endpoints;

import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.util.Constants;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Response;

public interface StructuredDataReferenceRest {
  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Get all structureddata references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY,implementation = StructuredDataReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllStructuredDataReferences(long collectionId, long dataObjectId);

  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Get structureddata reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getStructuredDataReference(long collectionId, long dataObjectId, long referenceId);

  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Create a new structureddata reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createStructuredDataReference(
    long collectionId,
    long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = StructuredDataReferenceIO.class))
    ) @Valid StructuredDataReferenceIO structuredDataReference
  );

  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Delete structureddata reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteStructuredDataReference(long collectionId, long dataObjectId, long structuredDataReferenceId);

  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Get structured data payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY,implementation = StructuredDataPayload.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getStructuredDataPayload(long collectionId, long dataObjectId, long structuredDataId);

  @Tag(name = Constants.STRUCTUREDDATA_REFERENCE)
  @Operation(description = "Get a specific structured data payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataPayload.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getSpecificStructuredDataPayload(long collectionId, long dataObjectId, long structuredDataId, String oid);
}
