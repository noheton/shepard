package de.dlr.shepard.v2.timeseries.resources;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * APISIMP-CROSS-BULK-KIND-PATH tombstone — {@code POST /v2/data-objects/cross-timeseries-bulk}
 * has moved to {@code POST /v2/data-objects/cross-bulk?kind=timeseries}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/v2/data-objects/cross-timeseries-bulk")
@RequestScoped
@Tag(name = "Timeseries")
public class CrossDoBulkTombstoneRest {

  @POST
  @Operation(
    operationId = "crossDoBulkGone",
    summary = "Gone — use POST /v2/data-objects/cross-bulk?kind=timeseries",
    deprecated = true
  )
  @APIResponse(responseCode = "410", description = "Endpoint has moved; see Location header.")
  public Response post() {
    return Response.status(Response.Status.GONE)
        .header("Location", "/v2/data-objects/cross-bulk?kind=timeseries")
        .build();
  }
}
