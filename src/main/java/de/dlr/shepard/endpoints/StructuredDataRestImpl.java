package de.dlr.shepard.endpoints;

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

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.services.StructuredDataContainerService;
import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.STRUCTUREDDATAS)
@Log4j2
public class StructuredDataRestImpl implements StructuredDataRest {
	private StructuredDataContainerService structuredDataContainerService = new StructuredDataContainerService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllStructuredDataContainer() {
		log.info("Received GET ALL STRUCTURED DATA CONTAINER request from user {}",
				securityContext.getUserPrincipal().getName());
		var containers = structuredDataContainerService.getAllStructuredDataContainers();
		var result = new ArrayList<StructuredDataContainerIO>(containers.size());
		for (var container : containers) {
			result.add(new StructuredDataContainerIO(container));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}")
	@Override
	public Response getStructuredDataContainer(
			@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId) {
		log.info("Received GET STRUCTURED DATA CONTAINER request with container Id {} from user {}", structuredDataId,
				securityContext.getUserPrincipal().getName());
		var result = structuredDataContainerService.getStructuredDataContainer(structuredDataId);
		return Response.ok(new StructuredDataContainerIO(result)).build();
	}

	@DELETE
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}")
	@Override
	public Response deleteStructuredDataContainer(
			@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId) {
		log.info("Received DELETE STRUCTURED DATA CONTAINER request with container Id {} from user {}",
				structuredDataId, securityContext.getUserPrincipal().getName());
		var result = structuredDataContainerService.deleteStructuredDataContainer(structuredDataId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

	@POST
	@Override
	public Response createStructuredDataContainer(StructuredDataContainerIO structuredDataContainerIO) {
		log.info("Received CREATE STRUCTUREDDATACONTAINER request from user {}",
				securityContext.getUserPrincipal().getName());
		var result = structuredDataContainerService.createStructuredDataContainer(structuredDataContainerIO,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new StructuredDataContainerIO(result)).status(HttpStatus.SC_CREATED).build();
	}

	@POST
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/payload")
	@Override
	public Response createStructuredData(@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId,
			StructuredDataPayload payload) {
		log.info("Received POST STRUCTUREDDATA request from user {}", securityContext.getUserPrincipal().getName());
		var result = structuredDataContainerService.createStructuredData(structuredDataId, payload);
		return result != null ? Response.status(HttpStatus.SC_CREATED).entity(result).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/payload")
	@Override
	public Response getAllStructuredDatas(@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId) {
		log.info("Received GET ALL STRUCTURED DATAS request with container Id {} from user {}", structuredDataId,
				securityContext.getUserPrincipal().getName());
		var result = structuredDataContainerService.getStructuredDataContainer(structuredDataId).getStructuredDatas();
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
	@Override
	public Response getStructuredData(@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId,
			@PathParam(Constants.OID) String oid) {
		log.info("Received GET STRUCTURED DATA request with container Id {} and Oid {} from user {}", structuredDataId,
				oid, securityContext.getUserPrincipal().getName());
		var result = structuredDataContainerService.getStructuredData(structuredDataId, oid);
		return result != null ? Response.ok(result).build() : Response.status(HttpStatus.SC_NOT_FOUND).build();
	}

}
