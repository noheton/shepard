package de.dlr.shepard.endpoints;

import de.dlr.shepard.services.ExperimentalTimeseriesService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("experimental/timeseriesContainers")
public class ExperimentalTimeseriesRest {

  @Inject
  ExperimentalTimeseriesService timeseriesService;

  @GET
  public Response getAllTimeserieses() {
    return null;
  }

  @POST
  @Path("/generate/{count}")
  public void generateTestData(@PathParam("count") int count) {}
}
