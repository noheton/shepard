package de.dlr.shepard.plugins.minter.datacite.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient.DataciteHttpResponse;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteCredentialIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteTestConnectionIO;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * APISIMP-MINTER-CRED-CONFIG-UNIFY — admin REST surface for the DataCite Fabrica minter.
 *
 * <p>Config fields and credentials are now managed via
 * {@code PATCH /v2/admin/config/minter-datacite} (V2CONV-A7): pass a {@code "password"}
 * field to set or clear the credential. The bespoke
 * {@code POST/DELETE .../credential} sub-resources are tombstoned (410 Gone).
 *
 * <p>{@code POST .../test-connection} is retained as a standalone diagnostic action.
 *
 * @see DataciteMinterConfigService
 */
@Path("/v2/admin/minters/datacite")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class DataciteAdminRest {

  private static final String GONE_MSG =
    "This endpoint has been removed. Use PATCH /v2/admin/config/minter-datacite " +
    "with a 'password' field to set the credential, or 'password': null to clear it.";

  @Inject
  DataciteMinterConfigService service;

  @Inject
  DataciteHttpClient http;

  // ─── POST /credential (tombstoned) ──────────────────────────────

  @POST
  @Path("/credential")
  @Operation(
    operationId = "setDataciteMinterCredential_gone",
    summary = "[Gone] Use PATCH /v2/admin/config/minter-datacite with a 'password' field.",
    deprecated = true
  )
  @APIResponse(responseCode = "410", description = "Endpoint removed. Use PATCH /v2/admin/config/minter-datacite.")
  public Response setCredential(DataciteCredentialIO body, @Context SecurityContext security) {
    return Response.status(Response.Status.GONE).entity(GONE_MSG).build();
  }

  // ─── DELETE /credential (tombstoned) ────────────────────────────

  @DELETE
  @Path("/credential")
  @Operation(
    operationId = "clearDataciteMinterCredential_gone",
    summary = "[Gone] Use PATCH /v2/admin/config/minter-datacite with 'password': null to clear.",
    deprecated = true
  )
  @APIResponse(responseCode = "410", description = "Endpoint removed. Use PATCH /v2/admin/config/minter-datacite.")
  public Response clearCredential(@Context SecurityContext security) {
    return Response.status(Response.Status.GONE).entity(GONE_MSG).build();
  }

  // ─── POST /test-connection ──────────────────────────────────────

  @POST
  @Path("/test-connection")
  @Operation(
    operationId = "testDataciteMinterConnection",
    summary = "Diagnose connectivity to the configured DataCite API.",
    description = "Issues a GET against <apiBaseUrl>/heartbeat (DataCite's documented " +
    "uptime endpoint) and reports reachable/statusCode/latency. Useful for operators to " +
    "verify config before enabling minting."
  )
  @APIResponse(
    responseCode = "200",
    description = "Diagnostic result. reachable=true means the API responded; reachable=false " +
    "means network or non-2xx.",
    content = @Content(schema = @Schema(implementation = DataciteTestConnectionIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response testConnection() {
    DataciteMinterConfig cfg = service.current();
    String baseUrl = cfg.getApiBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      return Response.ok(
        new DataciteTestConnectionIO(false, 0, 0L, null, "apiBaseUrl is not configured")
      ).build();
    }
    String url = stripTrailingSlash(baseUrl) + "/heartbeat";
    long t0 = System.nanoTime();
    DataciteHttpResponse response = http.getDiagnostic(url, null);
    long latencyMs = (System.nanoTime() - t0) / 1_000_000L;
    boolean reachable = response.statusCode() >= 200 && response.statusCode() < 400;
    String detail = reachable ? null : response.body();
    return Response.ok(
      new DataciteTestConnectionIO(reachable, response.statusCode(), latencyMs, baseUrl, detail)
    ).build();
  }

  // ─── helpers ────────────────────────────────────────────────────

  private static String stripTrailingSlash(String s) {
    if (s == null) return "";
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }
}
