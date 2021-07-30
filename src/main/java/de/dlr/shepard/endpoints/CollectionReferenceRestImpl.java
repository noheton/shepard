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

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.CollectionIO;
import de.dlr.shepard.neo4Core.io.CollectionReferenceIO;
import de.dlr.shepard.neo4Core.services.CollectionReferenceService;
import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.COLLECTION_REFERENCES)
@Log4j2
public class CollectionReferenceRestImpl implements CollectionReferenceRest {
	private CollectionReferenceService collectionReferenceService = new CollectionReferenceService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllCollectionReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
		log.info("Received GET ALL request from user {}", securityContext.getUserPrincipal().getName());
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
		log.info("Received GET request with reference Id {} from user {}", collectionReferenceId,
				securityContext.getUserPrincipal().getName());
		var result = collectionReferenceService.getCollectionReference(collectionReferenceId);
		return Response.ok(new CollectionReferenceIO(result)).build();
	}

	@POST
	@Override
	public Response createCollectionReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, CollectionReferenceIO collectionReference)
			throws InvalidBodyException {
		log.info("Received POST request with from user {}", securityContext.getUserPrincipal().getName());
		var result = collectionReferenceService.createCollectionReference(dataObjectId, collectionReference,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new CollectionReferenceIO(result)).status(HttpStatus.SC_CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.COLLECTION_REFERENCE_ID + "}")
	@Override
	public Response deleteCollectionReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.COLLECTION_REFERENCE_ID) long collectionReferenceId) {
		log.info("Received DELETE request with reference Id {} from user {}", collectionReferenceId,
				securityContext.getUserPrincipal().getName());
		var result = collectionReferenceService.deleteCollectionReference(collectionReferenceId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.COLLECTION_REFERENCE_ID + "}/payload")
	@Override
	public Response getCollectionReferencePayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.COLLECTION_REFERENCE_ID) long collectionReferenceId) {
		log.info("Received GET PAYLOAD request with reference Id {} from user {}", collectionReferenceId,
				securityContext.getUserPrincipal().getName());
		var payload = collectionReferenceService.getPayload(collectionReferenceId);
		return Response.ok(new CollectionIO(payload) {
		}).build();
	}

}
