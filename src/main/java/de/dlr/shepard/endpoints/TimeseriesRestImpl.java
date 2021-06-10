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

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.neo4Core.services.TimeseriesContainerService;
import de.dlr.shepard.util.Constants;
import lombok.extern.log4j.Log4j2;

@Subscribable
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path(Constants.TIMESERIES)
@Log4j2
public class TimeseriesRestImpl implements TimeseriesRest {
	private TimeseriesContainerService timeseriesContainerService = new TimeseriesContainerService();

	@Context
	private SecurityContext securityContext;

	@Override
	public Response getAllTimeseriesContainer() {
		log.info("Received GET ALL request from user {}", securityContext.getUserPrincipal().getName());
		var containers = timeseriesContainerService.getAllTimeseriesContainers();
		var result = new ArrayList<TimeseriesContainerIO>(containers.size());
		for (var container : containers) {
			result.add(new TimeseriesContainerIO(container));
		}
		return Response.ok(result).build();
	}

	@Override
	public Response getTimeseriesContainer(long timeseriesId) {
		log.info("Received GET request with container Id {} from user {}", timeseriesId,
				securityContext.getUserPrincipal().getName());

		var result = timeseriesContainerService.getTimeseriesContainer(timeseriesId);

		return Response.ok(new TimeseriesContainerIO(result)).build();
	}

	@Override
	public Response createTimeseriesContainer(TimeseriesContainerIO timeseriesContainer) {
		log.info("Received POST request with from user {}", securityContext.getUserPrincipal().getName());

		var result = timeseriesContainerService.createTimeseriesContainer(timeseriesContainer,
				securityContext.getUserPrincipal().getName());

		return Response.ok(new TimeseriesContainerIO(result)).status(HttpStatus.SC_CREATED).build();
	}

	@Override
	public Response deleteTimeseriesContainer(long timeseriesId) {
		log.info("Received DELETE request with container Id {} from user {}", timeseriesId,
				securityContext.getUserPrincipal().getName());

		var result = timeseriesContainerService.deleteTimeseriesContainer(timeseriesId,
				securityContext.getUserPrincipal().getName());

		return result ? Response.status(HttpStatus.SC_NO_CONTENT).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

	@Override
	public Response createTimeseries(long timeseriesId, TimeseriesPayload payload) {
		log.info("Received POST TIMESERIES request from user {}", securityContext.getUserPrincipal().getName());

		var result = timeseriesContainerService.createTimeseries(timeseriesId, payload);

		return result != null ? Response.status(HttpStatus.SC_CREATED).entity(result).build()
				: Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
	}

}
