package de.dlr.shepard.endpoints;

import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.util.Constants;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

public interface FileRest {
  @Tag(name = Constants.FILE)
  @Operation(description = "Get all file containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = FileContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllFileContainers(
    String name,
    Integer page,
    Integer size,
    ContainerAttributes orderAttribute,
    Boolean orderDesc
  );

  @Tag(name = Constants.FILE)
  @Operation(description = "Get file container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = FileContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getFileContainer(long fileContainerId);

  @Tag(name = Constants.FILE)
  @Operation(description = "Create a new file container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = FileContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createFileContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = FileContainerIO.class))
    ) @Valid FileContainerIO fileContainer
  );

  @Tag(name = Constants.FILE)
  @Operation(description = "Delete file container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteFileContainer(long fileContainerId);

  @Tag(name = Constants.FILE)
  @Operation(description = "Get files")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ShepardFile.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getAllFiles(long fileContainerId);

  @Tag(name = Constants.FILE)
  @Operation(description = "Get file")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(
      mediaType = MediaType.APPLICATION_OCTET_STREAM,
      schema = @Schema(type = SchemaType.STRING, format = "binary")
    )
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getFile(long fileContainerId, String oid);

  @Tag(name = Constants.FILE)
  @Operation(description = "Delete file")
  @APIResponse(description = "ok", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  Response deleteFile(long fileContainerId, String oid);

  @Tag(name = Constants.FILE)
  @Operation(description = "Upload a new file")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = ShepardFile.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response createFile(
    long fileContainerId,
    @Parameter(
      required = true,
      schema = @Schema(type = SchemaType.STRING, format = "binary", description = "File which you want to upload")
    ) InputStream fileInputStream,
    @Parameter(hidden = true) FormDataContentDisposition fileMetaData
  );

  @Tag(name = Constants.FILE)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getFilePermissions(long fileContainerId);

  @Tag(name = Constants.FILE)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response editFilePermissions(
    long fileContainerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  );

  @Tag(name = Constants.FILE)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = RolesIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  Response getFileRoles(long fileId);
}
