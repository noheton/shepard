package de.dlr.shepard.endpoints;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.dlr.shepard.exceptions.InvalidAuthException;
import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.influxDB.FillOption;
import de.dlr.shepard.influxDB.SingleValuedUnaryFunction;
import de.dlr.shepard.influxDB.TimeseriesPayload;
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
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.COLLECTIONS + "/{" + Constants.COLLECTION_ID + "}/" + Constants.DATAOBJECTS + "/{"
		+ Constants.DATAOBJECT_ID + "}/" + Constants.TIMESERIES_REFERENCES)
public class TimeseriesReferenceRestImpl implements TimeseriesReferenceRest {
	private TimeseriesReferenceService timeseriesReferenceService = new TimeseriesReferenceService();

	@Context
	private SecurityContext securityContext;

	@GET
	@Override
	public Response getAllTimeseriesReferences(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId) {
		var references = timeseriesReferenceService.getAllReferences(dataObjectId);
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
		var result = timeseriesReferenceService.getReference(timeseriesId);

		return Response.ok(new TimeseriesReferenceIO(result)).build();
	}

	@POST
	@Subscribable
	@Override
	public Response createTimeseriesReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId, TimeseriesReferenceIO timeseriesReference) {
		var result = timeseriesReferenceService.createReference(dataObjectId, timeseriesReference,
				securityContext.getUserPrincipal().getName());

		return Response.ok(new TimeseriesReferenceIO(result)).status(Status.CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}")
	@Subscribable
	@Override
	public Response deleteTimeseriesReference(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesId) {
		var result = timeseriesReferenceService.deleteReference(timeseriesId,
				securityContext.getUserPrincipal().getName());

		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@GET
	@Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}/payload")
	@Override
	public Response getTimeseriesPayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesId,
			@QueryParam(Constants.FUNCTION) SingleValuedUnaryFunction function,
			@QueryParam(Constants.GROUP_BY) Long groupBy, @QueryParam(Constants.FILLOPTION) FillOption fillOption,
			@QueryParam(Constants.DEVICE) Set<String> deviceFilterTag,
			@QueryParam(Constants.LOCATION) Set<String> locationFilterTag,
			@QueryParam(Constants.SYMBOLICNAME) Set<String> symbolicNameFilterTag) {
		List<TimeseriesPayload> payload;
		try {
			payload = timeseriesReferenceService.getPayload(timeseriesId, function, groupBy, fillOption,
					deviceFilterTag, locationFilterTag, symbolicNameFilterTag,
					securityContext.getUserPrincipal().getName());
			return Response.ok(payload).build();
		} catch (InvalidAuthException e) {
			return Response.status(Status.FORBIDDEN).build();
		}
	}

	@GET
	@Path("/{" + Constants.TIMESERIES_REFERENCE_ID + "}/" + Constants.EXPORT)
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@Override
	public Response exportTimeseriesPayload(@PathParam(Constants.COLLECTION_ID) long collectionId,
			@PathParam(Constants.DATAOBJECT_ID) long dataObjectId,
			@PathParam(Constants.TIMESERIES_REFERENCE_ID) long timeseriesId,
			@QueryParam(Constants.FUNCTION) SingleValuedUnaryFunction function,
			@QueryParam(Constants.GROUP_BY) Long groupBy, @QueryParam(Constants.FILLOPTION) FillOption fillOption,
			@QueryParam(Constants.DEVICE) Set<String> deviceFilterTag,
			@QueryParam(Constants.LOCATION) Set<String> locationFilterTag,
			@QueryParam(Constants.SYMBOLICNAME) Set<String> symbolicNameFilterTag) throws IOException {
		InputStream stream;
		try {
			stream = timeseriesReferenceService.export(timeseriesId, function, groupBy, fillOption, deviceFilterTag,
					locationFilterTag, symbolicNameFilterTag, securityContext.getUserPrincipal().getName());
			return Response.ok(stream, MediaType.APPLICATION_OCTET_STREAM).build();
		} catch (InvalidAuthException e) {
			return Response.status(Status.FORBIDDEN).build();
		}
	}

}
