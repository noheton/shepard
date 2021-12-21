package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import de.dlr.shepard.neo4Core.services.URIReferenceService;
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
import lombok.extern.slf4j.Slf4j;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.URI_REFERENCES)
@Slf4j
public class URIReferenceRestImpl implements URIReferenceRest {
	private URIReferenceService uriReferenceService = new URIReferenceService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllUriReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
		log.info("Received GET ALL request with collection {} and dataobject {} from user {}", collectionId,
				dataObjectId, securityContext.getUserPrincipal().getName());

		var references = uriReferenceService.getAllURIReferences(dataObjectId);
		var result = new ArrayList<URIReferenceIO>(references.size());
		for (var ref : references) {
			result.add(new URIReferenceIO(ref));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.URI_REFERENCE_ID + "}")
	@Override
	public Response getUriReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.URI_REFERENCE_ID) long referenceId) {
		log.info("Received GET request with collection {}, dataobject {} and reference {} from user {}", collectionId,
				dataObjectId, referenceId, securityContext.getUserPrincipal().getName());

		var reference = uriReferenceService.getURIReference(referenceId);
		return Response.ok(new URIReferenceIO(reference)).build();
	}

	@POST
	@Subscribable
	@Override
	public Response createUriReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, URIReferenceIO timeseriesReference)
			throws InvalidBodyException {
		log.info("Received POST request with from user {}", securityContext.getUserPrincipal().getName());

		var result = uriReferenceService.createURIReference(dataObjectId, timeseriesReference,
				securityContext.getUserPrincipal().getName());

		return Response.ok(new URIReferenceIO(result)).status(Status.CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.URI_REFERENCE_ID + "}")
	@Subscribable
	@Override
	public Response deleteUriReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.URI_REFERENCE_ID) long referenceId) {
		log.info("Received DELETE request with collectionID {}, dataObjectID {} and basicReferenceID {} from user {}",
				collectionId, dataObjectId, referenceId, securityContext.getUserPrincipal().getName());

		return uriReferenceService.deleteURIReference(referenceId, securityContext.getUserPrincipal().getName())
				? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

}
