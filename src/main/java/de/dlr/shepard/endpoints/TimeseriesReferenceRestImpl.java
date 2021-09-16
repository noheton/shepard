package de.dlr.shepard.endpoints;

import java.util.ArrayList;
import java.util.Set;

import org.apache.http.HttpStatus;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.neo4Core.services.TimeseriesReferenceService;
import de.dlr.shepard.util.Constants;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.TIMESERIES_REFERENCES)
@Log4j2
public class TimeseriesReferenceRestImpl implements TimeseriesReferenceRest {
	private TimeseriesReferenceService timeseriesReferenceService = new TimeseriesReferenceService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllTimeseriesReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
		log.info("Received GET ALL request from user {}", securityContext.getUserPrincipal().getName());

		var references = timeseriesReferenceService.getAllTimeseriesReferences(dataObjectId);
		var result = new ArrayList<TimeseriesReferenceIO>(references.size());
		for (var reference : references) {
			result.add(new TimeseriesReferenceIO(reference));
		}

		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}")
	@Override
	public Response getTimeseriesReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesId) {
		log.info("Received GET request with reference Id {} from user {}", timeseriesId,
				securityContext.getUserPrincipal().getName());

		var result = timeseriesReferenceService.getTimeseriesReference(timeseriesId);

		return Response.ok(new TimeseriesReferenceIO(result)).build();
	}

	@POST
	@Override
	public Response createTimeseriesReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, TimeseriesReferenceIO timeseriesReference)
			throws InvalidBodyException {
		log.info("Received POST request with from user {}", securityContext.getUserPrincipal().getName());

		var result = timeseriesReferenceService.createTimeseriesReference(dataObjectId, timeseriesReference,
				securityContext.getUserPrincipal().getName());

		return Response.ok(new TimeseriesReferenceIO(result)).status(HttpStatus.SC_CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}")
	@Override
	public Response deleteTimeseriesReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesId) {
		log.info("Received DELETE request with reference Id {} from user {}", timeseriesId,
				securityContext.getUserPrincipal().getName());

		var result = timeseriesReferenceService.deleteTimeseriesReference(timeseriesId,
				securityContext.getUserPrincipal().getName());

		return result ? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}/payload")
	@Override
	public Response getTimeseriesPayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesId,
			@QueryParam(Constants.DEVICE) Set<String> deviceFilterTag,
			@QueryParam(Constants.LOCATION) Set<String> locationFilterTag,
			@QueryParam(Constants.SYMBOLICNAME) Set<String> symbolicNameFilterTag) {
		log.info("Received GET PAYLOAD request with reference Id {} from user {}", timeseriesId,
				securityContext.getUserPrincipal().getName());
		var payload = timeseriesReferenceService.getPayload(timeseriesId, deviceFilterTag, locationFilterTag,
				symbolicNameFilterTag);
		return Response.ok(payload).build();
	}

}
