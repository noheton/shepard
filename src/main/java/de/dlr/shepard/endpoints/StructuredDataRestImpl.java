package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.PermissionsIO;
import de.dlr.shepard.neo4Core.io.StructuredDataContainerIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.neo4Core.services.PermissionsService;
import de.dlr.shepard.neo4Core.services.StructuredDataContainerService;
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
@Path(Constants.STRUCTUREDDATAS)
@Log4j2
public class StructuredDataRestImpl implements StructuredDataRest {
	private StructuredDataContainerService structuredDataContainerService = new StructuredDataContainerService();
	private PermissionsService permissionsService = new PermissionsService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllStructuredDataContainers(@QueryParam(Constants.QP_NAME) String name,
			@QueryParam(Constants.QP_PAGE) Integer page, @QueryParam(Constants.QP_SIZE) Integer size,
			@QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) ContainerAttributes orderBy,
			@QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc) {
		log.info("Received GET ALL STRUCTURED DATA CONTAINER request from user {}",
				securityContext.getUserPrincipal().getName());

		var params = new QueryParamHelper();
		if (name != null)
			params = params.withName(name);
		if (page != null && size != null)
			params = params.withPageAndSize(page, size);
		if (orderBy != null)
			params = params.withOrderByAttribute(orderBy, orderDesc);
		var containers = structuredDataContainerService.getAllStructuredDataContainers(params,
				securityContext.getUserPrincipal().getName());
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
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@POST
	@Override
	public Response createStructuredDataContainer(StructuredDataContainerIO structuredDataContainerIO) {
		log.info("Received CREATE STRUCTUREDDATACONTAINER request from user {}",
				securityContext.getUserPrincipal().getName());
		var result = structuredDataContainerService.createStructuredDataContainer(structuredDataContainerIO,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new StructuredDataContainerIO(result)).status(Status.CREATED).build();
	}

	@POST
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/payload")
	@Override
	public Response createStructuredData(@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId,
			StructuredDataPayload payload) throws InvalidBodyException {
		log.info("Received POST STRUCTUREDDATA request from user {}", securityContext.getUserPrincipal().getName());
		var result = structuredDataContainerService.createStructuredData(structuredDataId, payload);
		return result != null ? Response.status(Status.CREATED).entity(result).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
		return result != null ? Response.ok(result).build() : Response.status(Status.NOT_FOUND).build();
	}

	@DELETE
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/payload/{" + Constants.OID + "}")
	@Override
	public Response deleteStructuredData(@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId,
			@PathParam(Constants.OID) String oid) {
		log.info("Received DELETE STRUCTURED DATA request with container Id {} and Oid {} from user {}",
				structuredDataId, oid, securityContext.getUserPrincipal().getName());
		var result = structuredDataContainerService.deleteStructuredData(structuredDataId, oid);
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
	@Override
	public Response getStructuredDataPermissions(
			@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId) {
		log.info("Received GET PERMISSIONS request from user {}", securityContext.getUserPrincipal().getName());
		var perms = permissionsService.getPermissionsByEntity(structuredDataId);
		return perms != null ? Response.ok(new PermissionsIO(perms)).build()
				: Response.status(Status.NOT_FOUND).build();
	}

	@PUT
	@Path("/{" + Constants.STRUCTUREDDATA_CONTAINER_ID + "}/" + Constants.PERMISSIONS)
	@Override
	public Response editStructuredDataPermissions(
			@PathParam(Constants.STRUCTUREDDATA_CONTAINER_ID) long structuredDataId, @Valid PermissionsIO permissions) {
		log.info("Received PUT PERMISSIONS request from user {}", securityContext.getUserPrincipal().getName());
		var perms = permissionsService.updatePermissions(permissions, structuredDataId);
		return perms != null ? Response.ok(new PermissionsIO(perms)).build()
				: Response.status(Status.NOT_FOUND).build();
	}

}
