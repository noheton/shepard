package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.URIReferenceIO;
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

public interface URIReferenceRest {
  @Tag(name = Constants.URI_REFERENCE)
  @Operation(description = "Get all uri references")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = URIReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllUriReferences(long collectionId, long dataObjectId);

  @Tag(name = Constants.URI_REFERENCE)
  @Operation(description = "Get uri reference")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = URIReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getUriReference(long collectionId, long dataObjectId, long referenceId);

  @Tag(name = Constants.URI_REFERENCE)
  @Operation(description = "Create a new uri reference")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = URIReferenceIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createUriReference(
    long collectionId,
    long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = URIReferenceIO.class))
    ) @Valid URIReferenceIO timeseriesReference
  );

  @Tag(name = Constants.URI_REFERENCE)
  @Operation(description = "Delete uri reference")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteUriReference(long collectionId, long dataObjectId, long referenceId);
}
