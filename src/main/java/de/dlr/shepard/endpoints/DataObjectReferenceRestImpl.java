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
import de.dlr.shepard.neo4Core.io.AbstractDataObjectIO;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import de.dlr.shepard.neo4Core.services.DataObjectReferenceService;
import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)

@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.DATAOBJECT_REFERENCES)
@Log4j2
public class DataObjectReferenceRestImpl implements DataObjectReferenceRest {
	private DataObjectReferenceService dataObjectReferenceService = new DataObjectReferenceService();

	@Context
	private SecurityContext securityContext;

	@Override
	public Response getAllDataObjectReferences(long collectionId, long dataObjectId) {
		log.info("Received GET ALL request from user {}", securityContext.getUserPrincipal().getName());
		var references = dataObjectReferenceService.getAllDataObjectReferences(dataObjectId);
		var result = new ArrayList<DataObjectReferenceIO>(references.size());
		for (var reference : references) {
			result.add(new DataObjectReferenceIO(reference));
		}
		return Response.ok(result).build();
	}

	@Override
	public Response getDataObjectReference(long collectionId, long dataObjectId, long dataObjectReferenceId) {
		log.info("Received GET request with reference Id {} from user {}", dataObjectReferenceId,
				securityContext.getUserPrincipal().getName());
		var result = dataObjectReferenceService.getDataObjectReference(dataObjectReferenceId);
		return Response.ok(new DataObjectReferenceIO(result)).build();
	}

	@Override
	public Response createDataObjectReference(long collectionId, long dataObjectId,
			DataObjectReferenceIO dataObjectReference) throws InvalidBodyException {
		log.info("Received POST request with from user {}", securityContext.getUserPrincipal().getName());
		var result = dataObjectReferenceService.createDataObjectReference(dataObjectId, dataObjectReference,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new DataObjectReferenceIO(result)).status(HttpStatus.SC_CREATED).build();
	}

	@Override
	public Response deleteDataObjectReference(long collectionId, long dataObjectId, long dataObjectReferenceId) {
		log.info("Received DELETE request with reference Id {} from user {}", dataObjectReferenceId,
				securityContext.getUserPrincipal().getName());
		var result = dataObjectReferenceService.deleteDataObjectReference(dataObjectReferenceId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

	@Override
	public Response getDataObjectReferencePayload(long collectionId, long dataObjectId, long dataObjectReferenceId) {
		log.info("Received GET PAYLOAD request with reference Id {} from user {}", dataObjectReferenceId,
				securityContext.getUserPrincipal().getName());
		var payload = dataObjectReferenceService.getPayload(dataObjectReferenceId);
		return Response.ok(new AbstractDataObjectIO(payload) {
		}).build();
	}

}
