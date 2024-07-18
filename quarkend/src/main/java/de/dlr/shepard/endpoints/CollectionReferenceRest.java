package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.CollectionReferenceIO;
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

public interface CollectionReferenceRest {
  @Tag(name = Constants.COLLECTION_REFERENCE)
  @Operation(description = "Get all collection references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = CollectionReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllCollectionReferences(long collectionId, long dataObjectId);

  @Tag(name = Constants.COLLECTION_REFERENCE)
  @Operation(description = "Get collection reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CollectionReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getCollectionReference(long collectionId, long dataObjectId, long collectionReferenceId);

  @Tag(name = Constants.COLLECTION_REFERENCE)
  @Operation(description = "Create a new collection reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = CollectionReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createCollectionReference(
    long collectionId,
    long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CollectionReferenceIO.class))
    ) @Valid CollectionReferenceIO collectionReference
  );

  @Tag(name = Constants.COLLECTION_REFERENCE)
  @Operation(description = "Delete collection reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteCollectionReference(long collectionId, long dataObjectId, long collectionReferenceId);

  @Tag(name = Constants.COLLECTION_REFERENCE)
  @Operation(description = "Get collection reference payload")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CollectionIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getCollectionReferencePayload(long collectionId, long dataObjectId, long collectionReferenceId);
}
