package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
import de.dlr.shepard.neo4Core.services.DataObjectService;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
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
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS)
@Log4j2
public class DataObjectRestImpl implements DataObjectRest {
	private DataObjectService dataObjectService = new DataObjectService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllDataObjects(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@QueryParam(Constants.QP_NAME) String name, @QueryParam(Constants.QP_PAGE) Integer page,
			@QueryParam(Constants.QP_SIZE) Integer size, @QueryParam(Constants.QP_PARENT_ID) Long parentId,
			@QueryParam(Constants.QP_PREDECESSOR_ID) Long predecessorId,
			@QueryParam(Constants.QP_SUCCESSOR_ID) Long successorId,
			@QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) DataObjectAttributes orderBy,
			@QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc) {
		log.info("Received GET ALL request with collection {} from user {}", collectionId,
				securityContext.getUserPrincipal().getName());

		var params = new QueryParamHelper();
		if (name != null)
			params = params.withName(name);
		if (page != null && size != null)
			params = params.withPageAndSize(page, size);
		if (parentId != null)
			params = params.withParentId(parentId);
		if (predecessorId != null)
			params = params.withPredecessorId(predecessorId);
		if (successorId != null)
			params = params.withSuccessorId(successorId);
		if (orderBy != null)
			params = params.withOrderByAttribute(orderBy, orderDesc);

		var dataObjects = dataObjectService.getAllDataObjects(collectionId, params);
		var result = new ArrayList<DataObjectIO>(dataObjects.size());

		for (var dataObject : dataObjects) {
			result.add(new DataObjectIO(dataObject));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.DATAOBJECT_ID + "}")
	@Override
	public Response getDataObject(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
		log.info("Received GET request with collection {} from user {}", collectionId,
				securityContext.getUserPrincipal().getName());

		DataObject dataObject = dataObjectService.getDataObject(dataObjectId);
		return Response.ok(new DataObjectIO(dataObject)).build();
	}

	@POST
	@Override
	public Response createDataObject(@PathParam(Constants.COLLECTION_ID) long collectionId, DataObjectIO dataObject)
			throws InvalidBodyException {
		log.info("Received POST request with collection {} and new entity: name: {} from user {}", collectionId,
				dataObject.getName(), securityContext.getUserPrincipal().getName());

		DataObject newDataObject = dataObjectService.createDataObject(collectionId, dataObject,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new DataObjectIO(newDataObject)).status(Status.CREATED).build();
	}

	@PUT
	@Path("/{" + Constants.DATAOBJECT_ID + "}")
	@Override
	public Response updateDataObject(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, DataObjectIO dataObject)
			throws InvalidBodyException {
		log.info("Received PUT request with collection {} and new entity: name: {} from user {}", collectionId,
				dataObject.getName(), securityContext.getUserPrincipal().getName());

		DataObject updatedDataObject = dataObjectService.updateDataObject(dataObjectId, dataObject,
				securityContext.getUserPrincipal().getName());
		if (updatedDataObject == null) {
			return Response.status(Status.NOT_FOUND).build();
		}
		return Response.ok(new DataObjectIO(updatedDataObject)).build();
	}

	@DELETE
	@Path("/{" + Constants.DATAOBJECT_ID + "}")
	@Override
	public Response deleteDataObject(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
		log.info("Received DELETE request with parameters: collectionID: {}, dataObjectID: {} from user {}",
				collectionId, dataObjectId, securityContext.getUserPrincipal().getName());

		return dataObjectService.deleteDataObject(dataObjectId, securityContext.getUserPrincipal().getName())
				? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

}
