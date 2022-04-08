package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.DataObjectIO;
import de.dlr.shepard.neo4Core.io.DataObjectReferenceIO;
import de.dlr.shepard.neo4Core.services.DataObjectReferenceService;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.DATAOBJECT_REFERENCES)
public class DataObjectReferenceRestImpl implements DataObjectReferenceRest {
	private DataObjectReferenceService dataObjectReferenceService = new DataObjectReferenceService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllDataObjectReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
		var references = dataObjectReferenceService.getAllDataObjectReferences(dataObjectId);
		var result = new ArrayList<DataObjectReferenceIO>(references.size());
		for (var reference : references) {
			result.add(new DataObjectReferenceIO(reference));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.DATAOBJECT_REFERENCE_ID + "}")
	@Override
	public Response getDataObjectReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.DATAOBJECT_REFERENCE_ID) long dataObjectReferenceId) {
		var result = dataObjectReferenceService.getDataObjectReference(dataObjectReferenceId);
		return Response.ok(new DataObjectReferenceIO(result)).build();
	}

	@POST
	@Subscribable
	@Override
	public Response createDataObjectReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, DataObjectReferenceIO dataObjectReference)
			throws InvalidBodyException {
		var result = dataObjectReferenceService.createDataObjectReference(dataObjectId, dataObjectReference,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new DataObjectReferenceIO(result)).status(Status.CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.DATAOBJECT_REFERENCE_ID + "}")
	@Subscribable
	@Override
	public Response deleteDataObjectReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.DATAOBJECT_REFERENCE_ID) long dataObjectReferenceId) {
		var result = dataObjectReferenceService.deleteDataObjectReference(dataObjectReferenceId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.DATAOBJECT_REFERENCE_ID + "}/payload")
	@Override
	public Response getDataObjectReferencePayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.DATAOBJECT_REFERENCE_ID) long dataObjectReferenceId) {
		var payload = dataObjectReferenceService.getPayload(dataObjectReferenceId);
		return payload != null ? Response.ok(new DataObjectIO(payload)).build()
				: Response.status(Status.NOT_FOUND).build();
	}

}
