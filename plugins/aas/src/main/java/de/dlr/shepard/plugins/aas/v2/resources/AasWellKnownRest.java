package de.dlr.shepard.plugins.aas.v2.resources;

import de.dlr.shepard.plugins.aas.services.AasServerSelfDescriptionService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code GET /v2/aas/.well-known/aas-server} — self-description per
 * {@code aidocs/52 §4a.5}. Unauthenticated by design (only carries
 * capability flags + counts; never per-Shell identifiers).
 *
 * <p>Listed in
 * {@link de.dlr.shepard.common.filters.PublicEndpointRegistry} so
 * {@code JWTFilter} doesn't reject the call before the response is
 * built.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/v2/aas/.well-known/aas-server")
@RequestScoped
public class AasWellKnownRest {

  @Inject
  AasServerSelfDescriptionService service;

  @GET
  @Tag(name = "AAS")
  @Operation(
    summary = "AAS server self-description",
    description = "Discoverable JSON document describing this shepard's AAS integration: " +
    "API profile, supported submodel templates, shell count, and outbound registry " +
    "registrations. Unauthenticated. See `aidocs/52 §4a.5`."
  )
  @APIResponse(
    responseCode = "200",
    description = "Self-description payload (always — `enabled=false` indicates the operator hasn't opted in).",
    content = @Content(
      schema = @Schema(implementation = de.dlr.shepard.plugins.aas.v2.io.AasServerSelfDescriptionIO.class)
    )
  )
  public Response describe() {
    return Response.ok(service.describe()).build();
  }
}
