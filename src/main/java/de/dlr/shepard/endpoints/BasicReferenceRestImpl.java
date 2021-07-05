package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.http.HttpStatus;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.entities.BasicReference;
import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.neo4Core.services.BasicReferenceService;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.BASIC_REFERENCES)
@Log4j2
public class BasicReferenceRestImpl implements BasicReferenceRest {
	private BasicReferenceService basicReferenceService = new BasicReferenceService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, @QueryParam(Constants.QP_NAME) String name,
			@QueryParam(Constants.QP_PAGE) Integer page, @QueryParam(Constants.QP_SIZE) Integer size) {
		log.info("Received GET ALL request with collection {} and dataobject {} from user {}", collectionId,
				dataObjectId, securityContext.getUserPrincipal().getName());

		var params = new QueryParamHelper();
		if (name != null)
			params = params.withName(name);
		if (page != null && size != null)
			params = params.withPageAndSize(page, size);
		var references = basicReferenceService.getAllBasicReferences(dataObjectId, params);
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
		log.info("Received GET request with collection {}, dataobject {} and reference {} from user {}", collectionId,
				dataObjectId, referenceId, securityContext.getUserPrincipal().getName());

		BasicReference basicReference = basicReferenceService.getBasicReference(referenceId);
		return Response.ok(new BasicReferenceIO(basicReference)).build();
	}

	@DELETE
	@Path("/{" + Constants.BASIC_REFERENCE_ID + "}")
	@Override
	public Response deleteBasicReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.BASIC_REFERENCE_ID) long basicReferenceId) {
		log.info("Received DELETE request with collectionID {}, dataObjectID {} and basicReferenceID {} from user {}",
				collectionId, dataObjectId, basicReferenceId, securityContext.getUserPrincipal().getName());

		return basicReferenceService.deleteBasicReference(basicReferenceId,
				securityContext.getUserPrincipal().getName()) ? Response.status(HttpStatus.SC_NO_CONTENT).build()
						: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

}
