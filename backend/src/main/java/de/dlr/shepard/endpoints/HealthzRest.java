package de.dlr.shepard.endpoints;

import de.dlr.shepard.influxDB.InfluxDBConnector;
import de.dlr.shepard.mongoDB.MongoDBConnector;
import de.dlr.shepard.neo4Core.io.HealthzIO;
import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.IConnector;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.HEALTHZ)
@RequestScoped
public class HealthzRest {

  private static IConnector neo4j = NeoConnector.getInstance();
  private InfluxDBConnector influxdb;

  private MongoDBConnector mongodb;

  HealthzRest() {}

  @Inject
  public HealthzRest(InfluxDBConnector influxdb, MongoDBConnector mongodb) {
    this.influxdb = influxdb;
    this.mongodb = mongodb;
  }

  @GET
  @Tag(name = Constants.HEALTHZ)
  @Operation(description = "Get server health")
  @APIResponse(
    description = "ok",
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = HealthzIO.class))
  )
  @APIResponse(
    description = "not ok",
    responseCode = "503",
    content = @Content(schema = @Schema(implementation = HealthzIO.class))
  )
  public Response getServerHealth() {
    var neo4jAlive = neo4j.alive();
    var mongodbAlive = mongodb.alive();
    var influxdbAlive = influxdb.alive();
    var result = new HealthzIO(neo4jAlive, mongodbAlive, influxdbAlive);
    if (neo4jAlive && mongodbAlive && influxdbAlive) return Response.ok(result).build();
    Log.errorf("UNHEALTHY: %s", result);
    return Response.status(Status.SERVICE_UNAVAILABLE).entity(result).build();
  }
}
