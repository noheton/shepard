package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
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

public interface DataObjectRest {
  @Tag(name = Constants.DATAOBJECT)
  @Operation(description = "Get all dataObjects")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = DataObjectIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllDataObjects(
    long collectionId,
    String name,
    Integer page,
    Integer size,
    Long parentId,
    Long predecessorId,
    Long successorId,
    DataObjectAttributes orderAttribute,
    Boolean orderDesc
  );

  @Tag(name = Constants.DATAOBJECT)
  @Operation(description = "Get dataObject")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getDataObject(long collectionId, long dataObjectId);

  @Tag(name = Constants.DATAOBJECT)
  @Operation(description = "Create a new dataObject")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createDataObject(
    long collectionId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = DataObjectIO.class))
    ) @Valid DataObjectIO dataObject
  );

  @Tag(name = Constants.DATAOBJECT)
  @Operation(description = "Update dataObject")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = DataObjectIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response updateDataObject(
    long collectionId,
    long dataObjectId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = DataObjectIO.class))
    ) @Valid DataObjectIO dataObject
  );

  @Tag(name = Constants.DATAOBJECT)
  @Operation(description = "Delete dataObject")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteDataObject(long collectionId, long dataObjectId);
}
