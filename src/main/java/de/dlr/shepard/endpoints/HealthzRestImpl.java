package de.dlr.shepard.endpoints;

import de.dlr.shepard.influxDB.InfluxDBConnector;
import de.dlr.shepard.mongoDB.MongoDBConnector;
import de.dlr.shepard.neo4Core.io.HealthzIO;
import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.IConnector;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path(Constants.HEALTHZ)
public class HealthzRestImpl implements HealthzRest {

	private static IConnector neo4j = NeoConnector.getInstance();
	private static IConnector mongodb = MongoDBConnector.getInstance();
	private static IConnector influxdb = InfluxDBConnector.getInstance();

	@GET
	@Override
	public Response getServerHealth() {
		var neo4jAlive = neo4j.alive();
		var mongodbAlive = mongodb.alive();
		var influxdbAlive = influxdb.alive();
		var result = new HealthzIO(neo4jAlive, mongodbAlive, influxdbAlive);
		if (neo4jAlive && mongodbAlive && influxdbAlive)
			return Response.ok(result).build();
		log.error("UNHEALTY: {}", result);
		return Response.status(Status.SERVICE_UNAVAILABLE).entity(result).build();
	}

}
