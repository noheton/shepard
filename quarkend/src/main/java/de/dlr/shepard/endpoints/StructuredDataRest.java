package de.dlr.shepard.endpoints;

import de.dlr.shepard.mongoDB.StructuredData;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
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

public interface StructuredDataRest {
  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Get all structured data containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StructuredDataContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllStructuredDataContainers(
    String name,
    Integer page,
    Integer size,
    ContainerAttributes orderAttribute,
    Boolean orderDesc
  );

  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Get structured data container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getStructuredDataContainer(long structuredDataId);

  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Delete structured data container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteStructuredDataContainer(long structuredDataId);

  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Create a new structured data container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createStructuredDataContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = StructuredDataContainerIO.class))
    ) @Valid StructuredDataContainerIO structuredDataContainer
  );

  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Upload a new structured data object")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = StructuredData.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createStructuredData(
    long structuredDataId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = StructuredDataPayload.class))
    ) @Valid StructuredDataPayload payload
  );

  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Get structured data objects")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = StructuredData.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllStructuredDatas(long structuredDataId);

  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Download structured data")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = StructuredDataPayload.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getStructuredData(long structuredDataId, String oid);

  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Delete structured data")
  @APIResponse(description = "ok", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteStructuredData(long structuredDataId, String oid);

  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getStructuredDataPermissions(long structuredDataId);

  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response editStructuredDataPermissions(
    long structuredDataId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  );

  @Tag(name = Constants.STRUCTUREDDATA)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = RolesIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getStructuredDataRoles(long structuredDataId);
}
