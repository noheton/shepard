package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.http.HttpStatus;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.services.DataObjectService;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
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

	@Override
	public Response getAllDataObjects(long collectionId, String name, Integer page, Integer size, Long parentId) {
		log.info("Received GET ALL request with collection {} from user {}", collectionId,
				securityContext.getUserPrincipal().getName());

		var params = new QueryParamHelper();
		if (name != null)
			params = params.withName(name);
		if (page != null && size != null)
			params = params.withPageAndSize(page, size);
		if (parentId != null)
			params = params.withParentId(parentId);
		var dataObjects = dataObjectService.getAllDataObjects(collectionId, params);
		var result = new ArrayList<DataObjectIO>(dataObjects.size());

		for (var dataObject : dataObjects) {
			result.add(new DataObjectIO(dataObject));
		}
		return Response.ok(result).build();
	}

	@Override
	public Response getDataObject(long collectionId, long dataObjectId) {
		log.info("Received GET request with collection {} from user {}", collectionId,
				securityContext.getUserPrincipal().getName());

		DataObject dataObject = dataObjectService.getDataObject(dataObjectId);
		return Response.ok(new DataObjectIO(dataObject)).build();
	}

	@Override
	public Response createDataObject(long collectionId, DataObjectIO dataObject) throws InvalidBodyException {
		log.info("Received POST request with collection {} and new entity: name: {} from user {}", collectionId,
				dataObject.getName(), securityContext.getUserPrincipal().getName());

		DataObject newDataObject = dataObjectService.createDataObject(collectionId, dataObject,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new DataObjectIO(newDataObject)).status(HttpStatus.SC_CREATED).build();
	}

	@Override
	public Response updateDataObject(long collectionId, long dataObjectId, DataObjectIO dataObject)
			throws InvalidBodyException {
		log.info("Received PUT request with collection {} and new entity: name: {} from user {}", collectionId,
				dataObject.getName(), securityContext.getUserPrincipal().getName());

		DataObject updatedDataObject = dataObjectService.updateDataObject(dataObjectId, dataObject,
				securityContext.getUserPrincipal().getName());
		if (updatedDataObject == null) {
			return Response.status(HttpStatus.SC_NOT_FOUND).build();
		}
		return Response.ok(new DataObjectIO(updatedDataObject)).build();
	}

	@Override
	public Response deleteDataObject(long collectionId, long dataObjectId) {
		log.info("Received DELETE request with parameters: collectionID: {}, dataObjectID: {} from user {}",
				collectionId, dataObjectId, securityContext.getUserPrincipal().getName());

		return dataObjectService.deleteDataObject(dataObjectId, securityContext.getUserPrincipal().getName())
				? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

}
