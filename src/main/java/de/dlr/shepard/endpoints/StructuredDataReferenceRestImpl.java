package de.dlr.shepard.endpoints;

import java.util.ArrayList;
import java.util.List;

import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.mongoDB.StructuredDataPayload;
import de.dlr.shepard.neo4Core.io.StructuredDataReferenceIO;
import de.dlr.shepard.neo4Core.services.StructuredDataReferenceService;
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
		+ Constants.DATAOBJECT_ID + "}/" + Constants.STRUCTUREDDATA_REFERENCES)
public class StructuredDataReferenceRestImpl implements StructuredDataReferenceRest {
	private StructuredDataReferenceService structuredDataReferenceService = new StructuredDataReferenceService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllStructuredDataReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
		var references = structuredDataReferenceService.getAllStructuredDataReferences(dataObjectId);
		var result = new ArrayList<StructuredDataReferenceIO>(references.size());
		for (var ref : references) {
			result.add(new StructuredDataReferenceIO(ref));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.STRUCTUREDDATA_REFERENCE_ID + "}")
	@Override
	public Response getStructuredDataReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.STRUCTUREDDATA_REFERENCE_ID) long referenceId) {
		var ref = structuredDataReferenceService.getStructuredDataReference(referenceId);
		return Response.ok(new StructuredDataReferenceIO(ref)).build();
	}

	@POST
	@Subscribable
	@Override
	public Response createStructuredDataReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, StructuredDataReferenceIO structuredDataReference)
			throws InvalidBodyException {
		var ref = structuredDataReferenceService.createStructuredDataReference(dataObjectId, structuredDataReference,
				securityContext.getUserPrincipal().getName());
		return Response.ok(new StructuredDataReferenceIO(ref)).status(Status.CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.STRUCTUREDDATA_REFERENCE_ID + "}")
	@Subscribable
	@Override
	public Response deleteStructuredDataReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.STRUCTUREDDATA_REFERENCE_ID) long structuredDataReferenceId) {
		var result = structuredDataReferenceService.deleteReference(structuredDataReferenceId,
				securityContext.getUserPrincipal().getName());
		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.STRUCTUREDDATA_REFERENCE_ID + "}/payload")
	@Override
	public Response getStructuredDataPayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.STRUCTUREDDATA_REFERENCE_ID) long structuredDataId) {
		List<StructuredDataPayload> payload;
		try {
			payload = structuredDataReferenceService.getAllPayloads(structuredDataId,
					securityContext.getUserPrincipal().getName());
			return Response.ok(payload).build();
		} catch (InvalidAuthException e) {
			return Response.status(Status.FORBIDDEN).build();
		}
	}

	@GET
	@Path("/{" + Constants.STRUCTUREDDATA_REFERENCE_ID + "}/payload/{" + Constants.OID + "}")
	@Override
	public Response getSpecificStructuredDataPayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.STRUCTUREDDATA_REFERENCE_ID) long structuredDataId,
			@PathParam(Constants.OID) String oid) {
		StructuredDataPayload payload;
		try {
			payload = structuredDataReferenceService.getPayload(structuredDataId, oid,
					securityContext.getUserPrincipal().getName());
			return payload != null ? Response.ok(payload).build() : Response.status(Status.NOT_FOUND).build();
		} catch (InvalidAuthException e) {
			return Response.status(Status.FORBIDDEN).build();
		}
	}

}
