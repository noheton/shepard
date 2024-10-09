package de.dlr.shepard.endpoints;

import de.dlr.shepard.filters.Subscribable;
import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.influxDB.TimeseriesPayload;
import de.dlr.shepard.neo4Core.io.TimeseriesContainerIO;
import de.dlr.shepard.neo4Core.orderBy.ContainerAttributes;
import de.dlr.shepard.services.ExperimentalTimeseriesContainerService;
import de.dlr.shepard.services.TimeseriesContainerIOMapper;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.QueryParamHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
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
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("experimental-" + Constants.TIMESERIES_CONTAINERS)
@RequestScoped
public class ExperimentalTimeseriesRest {

  private ExperimentalTimeseriesContainerService timeseriesContainerService;

  @Context
  private SecurityContext securityContext;

  ExperimentalTimeseriesRest() {}

  @Inject
  public ExperimentalTimeseriesRest(
    ExperimentalTimeseriesContainerService timeseriesContainerService,
    SecurityContext securityContext
  ) {
    this.timeseriesContainerService = timeseriesContainerService;
    this.securityContext = securityContext;
  }

  @GET
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get all timeseries containers")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.QP_NAME)
  @Parameter(name = Constants.QP_PAGE)
  @Parameter(name = Constants.QP_SIZE)
  @Parameter(name = Constants.QP_ORDER_BY_ATTRIBUTE)
  @Parameter(name = Constants.QP_ORDER_DESC)
  public List<TimeseriesContainerIO> getAllTimeseriesesContainers(
    @QueryParam(Constants.QP_NAME) String name,
    @QueryParam(Constants.QP_PAGE) Integer page,
    @QueryParam(Constants.QP_SIZE) Integer size,
    @QueryParam(Constants.QP_ORDER_BY_ATTRIBUTE) ContainerAttributes orderBy,
    @QueryParam(Constants.QP_ORDER_DESC) Boolean orderDesc
  ) {
    var params = new QueryParamHelper();
    if (name != null) params = params.withName(name);
    if (page != null && size != null) params = params.withPageAndSize(page, size);
    if (orderBy != null) params = params.withOrderByAttribute(orderBy, orderDesc);
    var containers = timeseriesContainerService.getAllContainers(params, securityContext.getUserPrincipal().getName());
    var result = TimeseriesContainerIOMapper.map(containers);

    return result;
  }

  @GET
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Get timeseries container")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public TimeseriesContainerIO getTimeseriesContainer(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId
  ) {
    var container = timeseriesContainerService.getContainer(timeseriesContainerId);
    return TimeseriesContainerIOMapper.map(container);
  }

  @POST
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Create a new timeseries container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  public TimeseriesContainerIO createTimeseriesContainer(
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesContainerIO.class))
    ) @Valid TimeseriesContainerIO timeseriesContainer
  ) {
    var container = timeseriesContainerService.createContainer(
      timeseriesContainer,
      securityContext.getUserPrincipal().getName()
    );

    return TimeseriesContainerIOMapper.map(container);
  }

  @DELETE
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}")
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Delete timeseries container")
  @APIResponse(description = "deleted", responseCode = "204")
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public boolean deleteTimeseriesContainer(@PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesContainerId) {
    var result = timeseriesContainerService.deleteContainer(
      timeseriesContainerId,
      securityContext.getUserPrincipal().getName()
    );

    return result;
  }

  @POST
  @Path("/{" + Constants.TIMESERIES_CONTAINER_ID + "}/" + Constants.PAYLOAD)
  @Subscribable
  @Tag(name = Constants.TIMESERIES_CONTAINER)
  @Operation(description = "Upload timeseries to container")
  @APIResponse(
    description = "created",
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = Timeseries.class))
  )
  @APIResponse(description = "not found", responseCode = "404")
  @Parameter(name = Constants.TIMESERIES_CONTAINER_ID)
  public Timeseries createTimeseries(
    @PathParam(Constants.TIMESERIES_CONTAINER_ID) long timeseriesId,
    @RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = TimeseriesPayload.class))
    ) @Valid TimeseriesPayload payload
  ) {
    var timeseries = timeseriesContainerService.createTimeseries(timeseriesId, payload);
    return timeseries;
  }
}
