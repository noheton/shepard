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
import lombok.extern.log4j.Log4j2;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.FILES)
@Log4j2
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
		log.info("Received GET ALL FILE CONTAINER request from user {}", securityContext.getUserPrincipal().getName());

		var params = new QueryParamHelper();
		if (name != null)
			params = params.withName(name);
		if (page != null && size != null)
			params = params.withPageAndSize(page, size);
		if (orderBy != null)
			params = params.withOrderByAttribute(orderBy, orderDesc);
		var containers = fileContainerService.getAllFileContainers(params,
				securityContext.getUserPrincipal().getName());
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
		log.info("Received GET FILE CONTAINER request with container Id {} from user {}", fileContainerId,
				securityContext.getUserPrincipal().getName());
		var result = fileContainerService.getFileContainer(fileContainerId);
		return Response.ok(new FileContainerIO(result)).build();
	}

	@POST
	@Override
	public Response createFileContainer(FileContainerIO fileContainer) {
		log.info("Received CREATE FILE CONTAINER request from user {}", securityContext.getUserPrincipal().getName());
		var result = fileContainerService.createFileContainer(fileContainer,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new FileContainerIO(result)).status(Status.CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}")
	@Override
	public Response deleteFileContainer(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
		log.info("Received DELETE FILE CONTAINER request with container Id {} from user {}", fileContainerId,
				securityContext.getUserPrincipal().getName());
		var result = fileContainerService.deleteFileContainer(fileContainerId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload")
	@Override
	public Response getAllFiles(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
		log.info("Received GET ALL FILES request with container Id {} from user {}", fileContainerId,
				securityContext.getUserPrincipal().getName());
		var payload = fileContainerService.getFileContainer(fileContainerId).getFiles();
		return Response.ok(payload).build();
	}

	@GET
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@Override
	public Response getFile(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
			@PathParam(Constants.OID) String oid) {
		log.info("Received GET FILE request with container Id {} and Oid {} from user {}", fileContainerId, oid,
				securityContext.getUserPrincipal().getName());
		var payload = fileContainerService.getFile(fileContainerId, oid);
		return payload != null
				? Response.ok(payload.inputStream, MediaType.APPLICATION_OCTET_STREAM)
						.header("Content-Disposition", "attachment; filename=\"" + payload.name + "\"").build()
				: Response.status(Status.NOT_FOUND).build();
	}

	@DELETE
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
	@Override
	public Response deleteFile(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
			@PathParam(Constants.OID) String oid) {
		log.info("Received DELETE FILE request with container Id {} and Oid {} from user {}", fileContainerId, oid,
				securityContext.getUserPrincipal().getName());
		var result = fileContainerService.deleteFile(fileContainerId, oid);
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/payload")
	@Override
	public Response createFile(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
			@FormDataParam(Constants.FILE) InputStream fileInputStream,
			@FormDataParam(Constants.FILE) FormDataContentDisposition fileMetaData) {
		log.info("Received POST FILE request with file {} from user {}", fileMetaData,
				securityContext.getUserPrincipal().getName());
		String fileName = fileMetaData.getFileName();
		var result = fileContainerService.createFile(fileContainerId, fileName, fileInputStream);
		return result != null ? Response.status(Status.CREATED).entity(result).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
	@Override
	public Response getFilePermissions(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
		log.info("Received GET PERMISSIONS request from user {}", securityContext.getUserPrincipal().getName());
		var perms = permissionsService.getPermissionsByEntity(fileContainerId);
		return perms != null ? Response.ok(new PermissionsIO(perms)).build()
				: Response.status(Status.NOT_FOUND).build();
	}

	@PUT
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
	@Override
	public Response editFilePermissions(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId,
			@Valid PermissionsIO permissions) {
		log.info("Received PUT PERMISSIONS request from user {}", securityContext.getUserPrincipal().getName());
		var perms = permissionsService.updatePermissions(permissions, fileContainerId);
		return perms != null ? Response.ok(new PermissionsIO(perms)).build()
				: Response.status(Status.NOT_FOUND).build();
	}
}
