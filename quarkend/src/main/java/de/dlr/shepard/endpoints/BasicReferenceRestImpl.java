package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.neo4Core.orderBy.BasicReferenceAttributes;
import de.dlr.shepard.neo4Core.services.BasicReferenceService;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.BASIC_REFERENCES)
public class BasicReferenceRestImpl implements BasicReferenceRest {
	private BasicReferenceService basicReferenceService = new BasicReferenceService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, @QueryParam(Constants.QP_NAME) String name,
			@QueryParam(Constants.QP_PAGE) Integer page, @QueryParam(Constants.QP_SIZE) Integer size,
			@QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) BasicReferenceAttributes orderBy,
			@QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc) {
		var params = new QueryParamHelper();
		if (name != null)
			params = params.withName(name);
		if (page != null && size != null)
			params = params.withPageAndSize(page, size);
		if (orderBy != null)
			params = params.withOrderByAttribute(orderBy, orderDesc);
		var references = basicReferenceService.getAllBasicReferencesByDataObjectShepardId(dataObjectId, params);
		var result = new ArrayList<BasicReferenceIO>(references.size());

		for (var ref : references) {
			result.add(new BasicReferenceIO(ref));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.BASIC_REFERENCE_ID + "}")
	@Override
	public Response getBasicReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.BASIC_REFERENCE_ID) long referenceId) {
		BasicReference basicReference = basicReferenceService.getReferenceByShepardId(referenceId);
		return Response.ok(new BasicReferenceIO(basicReference)).build();
	}

	@DELETE
	@Path("/{" + Constants.BASIC_REFERENCE_ID + "}")
	@Subscribable
	@Override
	public Response deleteBasicReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId) {
		return basicReferenceService.deleteReferenceByShepardId(basicReferenceId,
				securityContext.getUserPrincipal().getName()) ? Response.status(Status.NO_CONTENT).build()
						: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

}
