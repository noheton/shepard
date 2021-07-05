package de.dlr.shepard.endpoints;

import java.io.InputStream;
import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.http.HttpStatus;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.FileContainerIO;
import de.dlr.shepard.neo4Core.services.FileContainerService;
import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.FILES)
@Log4j2
public class FileRestImpl implements FileRest {
	private FileContainerService fileContainerService = new FileContainerService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllFileContainers() {
		log.info("Received GET ALL FILE CONTAINER request from user {}", securityContext.getUserPrincipal().getName());
		var containers = fileContainerService.getAllFileContainers();
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
		return Response.ok(new FileContainerIO(result)).status(HttpStatus.SC_CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.FILE_CONTAINER_ID + "}")
	@Override
	public Response deleteFileContainer(@PathParam(Constants.FILE_CONTAINER_ID) long fileContainerId) {
		log.info("Received DELETE FILE CONTAINER request with container Id {} from user {}", fileContainerId,
				securityContext.getUserPrincipal().getName());
		var result = fileContainerService.deleteFileContainer(fileContainerId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
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
				: Response.status(HttpStatus.SC_NOT_FOUND).build();
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
		return result != null ? Response.status(HttpStatus.SC_CREATED).entity(result).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}
}
