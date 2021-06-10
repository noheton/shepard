package de.dlr.shepard.endpoints;

import java.io.InputStream;
import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.http.HttpStatus;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

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

	@Override
	public Response getFileContainer(long fileContainerId) {
		log.info("Received GET STRUCTURED DATA CONTAINER request with container Id {} from user {}", fileContainerId,
				securityContext.getUserPrincipal().getName());
		var result = fileContainerService.getFileContainer(fileContainerId);
		return Response.ok(new FileContainerIO(result)).build();
	}

	@Override
	public Response createFileContainer(FileContainerIO fileContainer) {
		log.info("Received CREATE FILECONTAINER request from user {}", securityContext.getUserPrincipal().getName());
		var result = fileContainerService.createFileContainer(fileContainer,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new FileContainerIO(result)).status(HttpStatus.SC_CREATED).build();
	}

	@Override
	public Response deleteFileContainer(long fileContainerId) {
		log.info("Received DELETE File CONTAINER request with container Id {} from user {}", fileContainerId,
				securityContext.getUserPrincipal().getName());
		var result = fileContainerService.deleteFileContainer(fileContainerId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

	@Override
	public Response createFile(long fileContainerId, InputStream inputStream, FormDataContentDisposition fileMetaData) {
		log.info("Received POST FILE request from user {}", securityContext.getUserPrincipal().getName());
		String fileName = fileMetaData.getFileName();
		var result = fileContainerService.createFile(fileContainerId, fileName, inputStream);
		return result != null ? Response.status(HttpStatus.SC_CREATED).entity(result).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}
}
