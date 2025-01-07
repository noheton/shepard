package de.dlr.shepard.data.timeseries.migration.endpoints;

import de.dlr.shepard.data.timeseries.migration.model.MigrationTaskEntity;
import de.dlr.shepard.data.timeseries.migration.services.TimeseriesMigrationService;
import de.dlr.shepard.data.timeseries.migration.services.TimeseriesMigrationTestDataIngestionService;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("temp/")
@RequestScoped
public class TimeseriesMigrationRest {

  @Context
  private SecurityContext securityContext;

  private TimeseriesMigrationService migrationService;

  private TimeseriesMigrationTestDataIngestionService testDataIngestionService;

  TimeseriesMigrationRest() {}

  @Inject
  TimeseriesMigrationRest(
    TimeseriesMigrationService timeseriesMigrationService,
    TimeseriesMigrationTestDataIngestionService testDataIngestionService
  ) {
    this.migrationService = timeseriesMigrationService;
    this.testDataIngestionService = testDataIngestionService;
  }

  @GET
  @Path("/migrations/state")
  @Tag(
    name = "timeseries migration",
    description = "This endpoint is only temporarily available. It is related to the migration of timeseries data from InfluxDB to TimescaleDB."
  )
  @Operation(
    description = "This endpoint is used to retrieve the current state of all data migrations that are currently running or already finished."
  )
  @APIResponse(
    responseCode = "200",
    description = "Returns information about the current state of all data migrations."
  )
  @APIResponse(responseCode = "401", description = "Unauthorized")
  @APIResponse(responseCode = "500", description = "Internal Server Error.")
  @Parameter(description = "show only tasks with errors", required = false)
  public Response getStateOfAllMigrations(@QueryParam("onlyShowErrors") boolean onlyShowErrors) {
    List<MigrationTaskEntity> tasks = migrationService.getMigrationTasks(onlyShowErrors);
    return Response.ok(tasks).build();
  }

  @POST
  @Path("/migrations/ingest")
  @Tag(name = "timeseries migration")
  @Parameter(name = "databaseName", required = true)
  @Parameter(name = "datasetSize", required = true)
  @Parameter(name = "dataPointValueType", required = true)
  @Operation(
    description = "Creates new database in influxdb, generate and insert random data to it to be use later in testing the migration."
  )
  @APIResponse(responseCode = "200", description = "Data inserted successfully.")
  @APIResponse(responseCode = "401", description = "Unauthorized")
  @APIResponse(responseCode = "500", description = "If database already exists.")
  public Response ingestData(
    @QueryParam("databaseName") String databaseName,
    @QueryParam("datasetSize") int datasetSize,
    @QueryParam("dataPointValueType") DataPointValueType dataPointValueType
  ) {
    testDataIngestionService.ingestTestData(
      databaseName,
      datasetSize,
      securityContext.getUserPrincipal().getName(),
      dataPointValueType
    );
    return Response.ok().build();
  }
}
