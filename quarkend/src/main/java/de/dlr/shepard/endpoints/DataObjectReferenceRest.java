package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

public interface DataObjectReferenceRest {
  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Get all dataObject references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = DataObjectReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllDataObjectReferences(long collectionId, long dataObjectId);

  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Get dataObject reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getDataObjectReference(long collectionId, long dataObjectId, long dataObjectReferenceId);

  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Create a new dataObject reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createDataObjectReference(
    long collectionId,
    long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = DataObjectReferenceIO.class))
    ) @Valid DataObjectReferenceIO dataObjectReference
  );

  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Delete dataObject reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteDataObjectReference(long collectionId, long dataObjectId, long dataObjectReferenceId);

  @Tag(name = Constants.DATAOBJECT_REFERENCE)
  @Operation(description = "Get dataObject reference payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getDataObjectReferencePayload(long collectionId, long dataObjectId, long dataObjectReferenceId);
}
