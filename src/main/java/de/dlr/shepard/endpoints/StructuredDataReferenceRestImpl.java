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
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.neo4Core.services.StructuredDataReferenceService;
import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.STRUCTUREDDATA_REFERENCES)
@Log4j2
public class StructuredDataReferenceRestImpl implements StructuredDataReferenceRest {
	private StructuredDataReferenceService structuredDataReferenceService = new StructuredDataReferenceService();

	@Context
	private SecurityContext securityContext;

	@Override
	public Response getAllStructuredDataReferences(long collectionId, long dataObjectId) {
		log.info("Received POST request with collection {} and dataobject {} from user {}", collectionId, dataObjectId,
				securityContext.getUserPrincipal().getName());
		var references = structuredDataReferenceService.getAllStructuredDataReferences(dataObjectId);
		var result = new ArrayList<StructuredDataReferenceIO>(references.size());
		for (var ref : references) {
			result.add(new StructuredDataReferenceIO(ref));
		}
		return Response.ok(result).build();
	}

	@Override
	public Response getStructuredDataReference(long collectionId, long dataObjectId, long referenceId) {
		log.info("Received POST request with collection {}, dataobject {} and reference {} from user {}", collectionId,
				dataObjectId, referenceId, securityContext.getUserPrincipal().getName());
		var ref = structuredDataReferenceService.getStructuredDataReference(referenceId);
		return Response.ok(new StructuredDataReferenceIO(ref)).build();
	}

	@Override
	public Response createStructuredDataReference(long collectionId, long dataObjectId,
			StructuredDataReferenceIO structuredDataReference) throws InvalidBodyException {
		log.info(
				"Received POST request with collection {}, dataobject {} and new structureddatareference {} from user {}",
				collectionId, dataObjectId, structuredDataReference.getName(),
				securityContext.getUserPrincipal().getName());
		var ref = structuredDataReferenceService.createStructuredDataReference(dataObjectId, structuredDataReference,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new StructuredDataReferenceIO(ref)).status(HttpStatus.SC_CREATED).build();
	}

	@Override
	public Response deleteBasicReference(long collectionId, long dataObjectId, long structuredDataReferenceId) {
		log.info(
				"Received DELETE request with parameters: collectionID {}, dataObjectID {}, structuredDataReferenceID {} from user {}",
				collectionId, dataObjectId, structuredDataReferenceId, securityContext.getUserPrincipal().getName());
		var result = structuredDataReferenceService.deleteReference(structuredDataReferenceId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

	@Override
	public Response getStructuredDataPayload(long collectionId, long dataObjectId, long structuredDataId) {
		log.info("Received GET STRUCTURED DATA PAYLOAD request with reference Id {} from user {}", structuredDataId,
				securityContext.getUserPrincipal().getName());
		var payload = structuredDataReferenceService.getAllPayloads(structuredDataId);
		return Response.ok(payload).build();
	}

	@Override
	public Response getSpecificStructuredDataPayload(long collectionId, long dataObjectId, long structuredDataId,
			String oid) {
		log.info("Received GET SPECIFIC STRUCTURED DATA PAYLOAD request with reference Id {} and Oid {} from user {}",
				structuredDataId, oid, securityContext.getUserPrincipal().getName());
		var payload = structuredDataReferenceService.getPayload(structuredDataId, oid);
		return Response.ok(payload).build();
	}

}
