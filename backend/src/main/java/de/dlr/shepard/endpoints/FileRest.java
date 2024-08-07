package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.mongoDB.ShepardFile;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.RolesIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.neo4Core.services.FileContainerService;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.FILES)
@RequestScoped
public class FileRest {

  private FileContainerService fileContainerService = new FileContainerService();
  private PermissionsService permissionsService = new PermissionsService();

  @Context
  private SecurityContext securityContext;

  @GET
  @Tag(name = Constants.FILE)
  @Operation(description = "Get all file containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = FileContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response getAllFileContainers(
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) Integer page,
    @QueryParam(Constants.QP_SIZE) Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) ContainerAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    var containers = fileContainerService.getAllContainers(params, securityContext.getUserPrincipal().getName());
    var result = new ArrayList<FileContainerIO>(containers.size());
    for (var container : containers) {
      result.add(new FileContainerIO(container));
    }
    return Response.ok(result).build();
  }

  @GET
  @Path("/{" + Constants.FILE_CONTAINER_ID + "}")
  @Tag(name = Constants.FILE)
  @Operation(description = "Get file container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = FileContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response getFileContainer(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
    var result = fileContainerService.getContainer(fileContainerId);
    return Response.ok(new FileContainerIO(result)).build();
  }

  @POST
  @Tag(name = Constants.FILE)
  @Operation(description = "Create a new file container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = FileContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response createFileContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = FileContainerIO.class))
    ) @Valid FileContainerIO fileContainer
  ) {
    var result = fileContainerService.createContainer(fileContainer, securityContext.getUserPrincipal().getName());
    return Response.ok(new FileContainerIO(result)).status(Status.CREATED).build();
  }

  @DELETE
  @Path("/{" + Constants.FILE_CONTAINER_ID + "}")
  @Subscribable
  @Tag(name = Constants.FILE)
  @Operation(description = "Delete file container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  public Response deleteFileContainer(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
    var result = fileContainerService.deleteContainer(fileContainerId, securityContext.getUserPrincipal().getName());
    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @GET
  @Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload")
  @Tag(name = Constants.FILE)
  @Operation(description = "Get files")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = ShepardFile.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response getAllFiles(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
    var payload = fileContainerService.getContainer(fileContainerId).getFiles();
    return Response.ok(payload).build();
  }

  @GET
  @Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
  @Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
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
  public Response getFile(
    @PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
    @PathParam(Constants.OID) String oid
  ) {
    var payload = fileContainerService.getFile(fileContainerId, oid);
    return payload != null
      ? Response.ok(payload.getInputStream(), MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=\"" + payload.getName() + "\"")
        .header("Content-Length", payload.getSize())
        .build()
      : Response.status(Status.NOT_FOUND).build();
  }

  @DELETE
  @Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
  @Subscribable
  @Tag(name = Constants.FILE)
  @Operation(description = "Delete file")
  @APIResponse(description = "ok", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  public Response deleteFile(
    @PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
    @PathParam(Constants.OID) String oid
  ) {
    var result = fileContainerService.deleteFile(fileContainerId, oid);
    return result ? Response.status(Status.NO_CONTENT).build() : Response.status(Status.INTERNAL_SERVER_ERROR).build();
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Tag(name = Constants.FILE)
  @Operation(description = "Upload a new file")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = ShepardFile.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload")
  @Subscribable
  public Response createFile(
    @PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
    MultipartBodyFileUpload body
  ) {
    String fileName = body.fileUpload != null ? body.fileUpload.fileName() : null;
    String filePath = body.fileUpload != null ? body.fileUpload.uploadedFile().toString() : null;

    if (filePath == null) {
      return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    File file = new File(filePath);
    try (InputStream fileInputStream = new FileInputStream(file)) {
      var result = fileContainerService.createFile(fileContainerId, fileName, fileInputStream);
      return result != null
        ? Response.status(Status.CREATED).entity(result).build()
        : Response.status(Status.INTERNAL_SERVER_ERROR).build();
    } catch (IOException e) {
      return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @Schema(type = SchemaType.STRING, format = "binary", description = "File which you want to upload")
  public interface UploadItemSchema {}

  public class UploadFormSchema {

    @Schema(required = true)
    public UploadItemSchema file;
  }

  @Schema(implementation = UploadFormSchema.class)
  public static class MultipartBodyFileUpload {

    @RestForm(Constants.FILE)
    public FileUpload fileUpload;
  }

  @GET
  @Path("/{" + Constants.FILE_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.FILE)
  @Operation(description = "Get permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response getFilePermissions(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
    var perms = permissionsService.getPermissionsByNeo4jId(fileContainerId);
    return perms != null ? Response.ok(new PermissionsIO(perms)).build() : Response.status(Status.NOT_FOUND).build();
  }

  @PUT
  @Path("/{" + Constants.FILE_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
  @Tag(name = Constants.FILE)
  @Operation(description = "Edit permissions")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = PermissionsIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response editFilePermissions(
    @PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = PermissionsIO.class))
    ) @Valid PermissionsIO permissions
  ) {
    var perms = permissionsService.updatePermissionsByNeo4jId(permissions, fileContainerId);
    return perms != null ? Response.ok(new PermissionsIO(perms)).build() : Response.status(Status.NOT_FOUND).build();
  }

  @GET
  @Path("/{" + Constants.FILE_CONTAINER_ID + "}/" + Constants.ROLES)
  @Tag(name = Constants.FILE)
  @Operation(description = "Get roles")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = RolesIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public Response getFileRoles(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
    var roles = new PermissionsUtil().getRolesByNeo4jId(fileContainerId, securityContext.getUserPrincipal().getName());
    return roles != null ? Response.ok(roles).build() : Response.status(Status.NOT_FOUND).build();
  }
}
