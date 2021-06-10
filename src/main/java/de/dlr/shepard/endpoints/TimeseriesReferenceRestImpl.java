package de.dlr.shepard.endpoints;

import java.util.ArrayList;
import java.util.Set;

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
import de.dlr.shepard.neo4Core.io.TimeseriesReferenceIO;
import de.dlr.shepard.neo4Core.services.TimeseriesReferenceService;
import de.dlr.shepard.util.Constants;
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

	@Override
	public Response getAllTimeseriesReferences(long collectionId, long dataObjectId) {
		log.info("Received GET ALL request from user {}", securityContext.getUserPrincipal().getName());

		var references = timeseriesReferenceService.getAllTimeseriesReferences(dataObjectId);
		var result = new ArrayList<TimeseriesReferenceIO>(references.size());
		for (var reference : references) {
			result.add(new TimeseriesReferenceIO(reference));
		}

		return Response.ok(result).build();
	}

	@Override
	public Response getTimeseriesReference(long collectionId, long dataObjectId, long timeseriesId) {
		log.info("Received GET request with reference Id {} from user {}", timeseriesId,
				securityContext.getUserPrincipal().getName());

		var result = timeseriesReferenceService.getTimeseriesReference(timeseriesId);

		return Response.ok(new TimeseriesReferenceIO(result)).build();
	}

	@Override
	public Response createTimeseriesReference(long collectionId, long dataObjectId,
			TimeseriesReferenceIO timeseriesReference) throws InvalidBodyException {
		log.info("Received POST request with from user {}", securityContext.getUserPrincipal().getName());

		var result = timeseriesReferenceService.createTimeseriesReference(dataObjectId, timeseriesReference,
				securityContext.getUserPrincipal().getName());

		return Response.ok(new TimeseriesReferenceIO(result)).status(HttpStatus.SC_CREATED).build();
	}

	@Override
	public Response deleteTimeseriesReference(long collectionId, long dataObjectId, long timeseriesId) {
		log.info("Received DELETE request with reference Id {} from user {}", timeseriesId,
				securityContext.getUserPrincipal().getName());

		var result = timeseriesReferenceService.deleteTimeseriesReference(timeseriesId,
				securityContext.getUserPrincipal().getName());

		return result ? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

	@Override
	public Response getTimeseriesPayload(long collectionId, long dataObjectId, long timeseriesId,
			Set<String> deviceFilterTag, Set<String> locationFilterTag, Set<String> symbolicNameFilterTag) {
		log.info("Received GET PAYLOAD request with reference Id {} from user {}", timeseriesId,
				securityContext.getUserPrincipal().getName());
		var payload = timeseriesReferenceService.getPayload(timeseriesId, deviceFilterTag, locationFilterTag,
				symbolicNameFilterTag);
		return Response.ok(payload).build();
	}

}
