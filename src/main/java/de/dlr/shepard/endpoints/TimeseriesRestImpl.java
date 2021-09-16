package de.dlr.shepard.endpoints;

import java.util.ArrayList;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.neo4Core.services.TimeseriesContainerService;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
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

	@GET
	@Override
	public Response getAllTimeseriesContainers(@QueryParam(Constants.QP_NAME) String name,
			@QueryParam(Constants.QP_PAGE) Integer page, @QueryParam(Constants.QP_SIZE) Integer size,
			@QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) ContainerAttributes orderBy,
			@QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc) {
		log.info("Received GET ALL request from user {}", securityContext.getUserPrincipal().getName());

		var params = new QueryParamHelper();
		if (name != null)
			params = params.withName(name);
		if (page != null && size != null)
			params = params.withPageAndSize(page, size);
		if (orderBy != null)
			params = params.withOrderByAttribute(orderBy, orderDesc);
		var containers = timeseriesContainerService.getAllTimeseriesContainers(params);
		var result = new ArrayList<TimeseriesContainerIO>(containers.size());
		for (var container : containers) {
			result.add(new TimeseriesContainerIO(container));
		}
		return Response.ok(result).build();
	}

	@GET
	@Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
	@Override
	public Response getTimeseriesContainer(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesId) {
		log.info("Received GET request with container Id {} from user {}", timeseriesId,
				securityContext.getUserPrincipal().getName());

		var result = timeseriesContainerService.getTimeseriesContainer(timeseriesId);

		return Response.ok(new TimeseriesContainerIO(result)).build();
	}

	@POST
	@Override
	public Response createTimeseriesContainer(TimeseriesContainerIO timeseriesContainer) {
		log.info("Received POST request with from user {}", securityContext.getUserPrincipal().getName());

		var result = timeseriesContainerService.createTimeseriesContainer(timeseriesContainer,
				securityContext.getUserPrincipal().getName());

		return Response.ok(new TimeseriesContainerIO(result)).status(Status.CREATED).build();
	}

	@DELETE
	@Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
	@Override
	public Response deleteTimeseriesContainer(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesId) {
		log.info("Received DELETE request with container Id {} from user {}", timeseriesId,
				securityContext.getUserPrincipal().getName());

		var result = timeseriesContainerService.deleteTimeseriesContainer(timeseriesId,
				securityContext.getUserPrincipal().getName());

		return result ? Response.status(Status.NO_CONTENT).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

	@POST
	@Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/payload")
	@Override
	public Response createTimeseries(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesId,
			TimeseriesPayload payload) {
		log.info("Received POST TIMESERIES request from user {}", securityContext.getUserPrincipal().getName());

		var result = timeseriesContainerService.createTimeseries(timeseriesId, payload);

		return result != null ? Response.status(Status.CREATED).entity(result).build()
				: Response.status(Status.INTERNAL_SERVER_ERROR).build();
	}

}
