package de.dlr.shepard.endpoints;

import java.io.InputStream;
import java.util.ArrayList;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.neo4Core.services.FileContainerService;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.security.PermissionsUtil;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
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

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.FILES)
public class FileRestImpl implements FileRest {
	private FileContainerService fileContainerService = new FileContainerService();
	private PermissionsService permissionsService = new PermissionsService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllFileContainers(@QueryParam(Constants.QP_NAME) String name,
			@QueryParam(Constants.QP_PAGE) Integer page, @QueryParam(Constants.QP_SIZE) Integer size,
			@QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) ContainerAttributes orderBy,
			@QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc) {
		var params = new QueryParamHelper();
		if (name != null)
			params = params.withName(name);
		if (page != null && size != null)
			params = params.withPageAndSize(page, size);
		if (orderBy != null)
			params = params.withOrderByAttribute(orderBy, orderDesc);
		var containers = fileContainerService.getAllContainers(params, securityContext.getUserPrincipal().getName());
		var result = new ArrayList<FileContainerIO>(containers.size());
		for (var container : containers) {
			result.add(new FileContainerIO(container));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}")
	@Override
	public Response getFileContainer(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
		var result = fileContainerService.getContainer(fileContainerId);
		return Response.ok(new FileContainerIO(result)).build();
	}

	@POST
	@Override
	public Response createFileContainer(FileContainerIO fileContainer) {
		var result = fileContainerService.createContainer(fileContainer, securityContext.getUserPrincipal().getName());
		return Response.ok(new FileContainerIO(result)).status(Status.CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}")
	@Subscribable
	@Override
	public Response deleteFileContainer(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
		var result = fileContainerService.deleteContainer(fileContainerId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload")
	@Override
	public Response getAllFiles(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
		var payload = fileContainerService.getContainer(fileContainerId).getFiles();
		return Response.ok(payload).build();
	}

	@GET
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
	@Produces({ MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON })
	@Override
	public Response getFile(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
			@PathParam(Constants.OID) String oid) {
		var payload = fileContainerService.getFile(fileContainerId, oid);
		return payload != null
				? Response.ok(payload.getInputStream(), MediaType.APPLICATION_OCTET_STREAM)
						.header("Content-Disposition", "attachment; filename=\"" + payload.getName() + "\"")
						.header("Content-Length", payload.getSize()).build()
				: Response.status(Status.NOT_FOUND).build();
	}

	@DELETE
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
	@Subscribable
	@Override
	public Response deleteFile(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
			@PathParam(Constants.OID) String oid) {
		var result = fileContainerService.deleteFile(fileContainerId, oid);
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload")
	@Subscribable
	@Override
	public Response createFile(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
			@FormDataParam(Constants.FILE) InputStream fileInputStream,
			@FormDataParam(Constants.FILE) FormDataContentDisposition fileMetaData) {
		String fileName = fileMetaData != null ? fileMetaData.getFileName() : null;
		var result = fileContainerService.createFile(fileContainerId, fileName, fileInputStream);
		return result != null ? Response.status(Status.CREATED).entity(result).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
	@Override
	public Response getFilePermissions(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
		var perms = permissionsService.getPermissionsByNeo4jId(fileContainerId);
		return perms != null ? Response.ok(new PermissionsIO(perms)).build()
				: Response.status(Status.NOT_FOUND).build();
	}

	@PUT
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
	@Override
	public Response editFilePermissions(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
			@Valid PermissionsIO permissions) {
		var perms = permissionsService.updatePermissionsByNeo4jId(permissions, fileContainerId);
		return perms != null ? Response.ok(new PermissionsIO(perms)).build()
				: Response.status(Status.NOT_FOUND).build();
	}

	@GET
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/" + Constants.ROLES)
	@Override
	public Response getFileRoles(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
		var roles = new PermissionsUtil().getRolesByNeo4jId(fileContainerId,
				securityContext.getUserPrincipal().getName());
		return roles != null ? Response.ok(roles).build() : Response.status(Status.NOT_FOUND).build();
	}

}
