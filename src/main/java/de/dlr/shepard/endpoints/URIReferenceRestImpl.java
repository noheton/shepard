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
import de.dlr.shepard.neo4Core.io.URIReferenceIO;
import de.dlr.shepard.neo4Core.services.URIReferenceService;
import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.URI_REFERENCES)
@Log4j2
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
	@Override
	public Response createUriReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, URIReferenceIO timeseriesReference)
			throws InvalidBodyException {
		log.info("Received POST request with from user {}", securityContext.getUserPrincipal().getName());

		var result = uriReferenceService.createURIReference(dataObjectId, timeseriesReference,
				securityContext.getUserPrincipal().getName());

		return Response.ok(new URIReferenceIO(result)).status(HttpStatus.SC_CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.URI_REFERENCE_ID + "}")
	@Override
	public Response deleteUriReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.URI_REFERENCE_ID) long referenceId) {
		log.info("Received DELETE request with collectionID {}, dataObjectID {} and basicReferenceID {} from user {}",
				collectionId, dataObjectId, referenceId, securityContext.getUserPrincipal().getName());

		return uriReferenceService.deleteURIReference(referenceId, securityContext.getUserPrincipal().getName())
				? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

}
