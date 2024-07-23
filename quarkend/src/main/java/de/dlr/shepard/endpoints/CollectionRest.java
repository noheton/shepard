package de.dlr.shepard.endpoints;

import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.io.VersionIO;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

public interface CollectionRest {
  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get all collections")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = CollectionIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllCollections(
    String name,
    Integer page,
    Integer size,
    DataObjectAttributes orderAttribute,
    Boolean orderDesc
  );

  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Create a new version")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = VersionIO.class))
  )
  Response createVersion(
    long collectionId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = VersionIO.class))
    ) @Valid VersionIO version
  );

  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get versions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = VersionIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getVersions(long collectionId);

  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get collection")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CollectionIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getCollection(long collectionId, String versionUID);

  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get version")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = VersionIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getVersion(long collectionId, String versionUID);

  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Create a new collection")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = CollectionIO.class))
  )
  Response createCollection(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CollectionIO.class))
    ) @Valid CollectionIO collection
  );

  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Update collection")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = CollectionIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response updateCollection(
    long collectionId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CollectionIO.class))
    ) @Valid CollectionIO collection
  );

  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Delete collection")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteCollection(long collectionId);

  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getCollectionPermissions(long collectionId);

  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response editCollectionPermissions(
    long collectionId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  );

  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = RolesIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getCollectionRoles(long collectionId);

  @Tag(name = Constants.COLLECTION)
  @Operation(description = "Export Collection as RoCrate")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response exportCollection(long collectionId) throws IOException;
}
