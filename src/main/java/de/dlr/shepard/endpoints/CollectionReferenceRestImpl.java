package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.CollectionReferenceIO;
import de.dlr.shepard.neo4Core.services.CollectionReferenceService;
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
		+ Constants.DATAOBJECT_ID + "}/" + Constants.COLLECTION_REFERENCES)
public class CollectionReferenceRestImpl implements CollectionReferenceRest {
	private CollectionReferenceService collectionReferenceService = new CollectionReferenceService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllCollectionReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
		var references = collectionReferenceService.getAllCollectionReferences(dataObjectId);
		var result = new ArrayList<CollectionReferenceIO>(references.size());
		for (var reference : references) {
			result.add(new CollectionReferenceIO(reference));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.COLLECTION_REFERENCE_ID + "}")
	@Override
	public Response getCollectionReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.COLLECTION_REFERENCE_ID) long collectionReferenceId) {
		var result = collectionReferenceService.getCollectionReference(collectionReferenceId);
		return Response.ok(new CollectionReferenceIO(result)).build();
	}

	@POST
	@Subscribable
	@Override
	public Response createCollectionReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, CollectionReferenceIO collectionReference)
			throws InvalidBodyException {
		var result = collectionReferenceService.createCollectionReference(dataObjectId, collectionReference,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new CollectionReferenceIO(result)).status(Status.CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.COLLECTION_REFERENCE_ID + "}")
	@Subscribable
	@Override
	public Response deleteCollectionReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.COLLECTION_REFERENCE_ID) long collectionReferenceId) {
		var result = collectionReferenceService.deleteCollectionReference(collectionReferenceId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.COLLECTION_REFERENCE_ID + "}/payload")
	@Override
	public Response getCollectionReferencePayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.COLLECTION_REFERENCE_ID) long collectionReferenceId) {
		var payload = collectionReferenceService.getPayload(collectionReferenceId);
		return payload != null ? Response.ok(new CollectionIO(payload)).build()
				: Response.status(Status.NOT_FOUND).build();
	}

}
