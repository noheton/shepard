package de.dlr.shepard.plugins.minter.epic.resources;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.minter.epic.daos.EpicHttpClient;
import de.dlr.shepard.plugins.minter.epic.daos.EpicHttpClient.EpicHttpResponse;
import de.dlr.shepard.plugins.minter.epic.entities.EpicMinterConfig;
import de.dlr.shepard.plugins.minter.epic.io.EpicCredentialIO;
import de.dlr.shepard.plugins.minter.epic.io.EpicTestConnectionIO;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService;
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
 * APISIMP-MINTER-CRED-CONFIG-UNIFY — admin REST surface for the ePIC handle service minter.
 *
 * <p>Config fields and credentials are now managed via
 * {@code PATCH /v2/admin/config/minter-epic} (V2CONV-A7): pass a {@code "credential"}
 * field to set or clear the credential. The bespoke
 * {@code POST/DELETE .../credential} sub-resources are tombstoned (410 Gone).
 *
 * <p>{@code POST .../test-connection} is retained as a standalone diagnostic action.
 *
 * @see EpicMinterConfigService
 */
@Path("/v2/admin/minters/epic")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class EpicAdminRest {

  private static final String GONE_MSG =
    "This endpoint has been removed. Use PATCH /v2/admin/config/minter-epic " +
    "with a 'credential' field to set the credential, or 'credential': null to clear it.";

  @Inject
  EpicMinterConfigService service;

  @Inject
  EpicHttpClient http;

  // ─── POST /credential (tombstoned) ──────────────────────────────

  @POST
  @Path("/credential")
  @Operation(
    operationId = "setEpicMinterCredential_gone",
    summary = "[Gone] Use PATCH /v2/admin/config/minter-epic with a 'credential' field.",
    deprecated = true
  )
  @APIResponse(responseCode = "410", description = "Endpoint removed. Use PATCH /v2/admin/config/minter-epic.")
  public Response setCredential(EpicCredentialIO body, @Context SecurityContext security) {
    return Response.status(Response.Status.GONE)
      .header("Location", "/v2/admin/config/minter-epic")
      .entity(GONE_MSG).build();
  }

  // ─── DELETE /credential (tombstoned) ────────────────────────────

  @DELETE
  @Path("/credential")
  @Operation(
    operationId = "clearEpicMinterCredential_gone",
    summary = "[Gone] Use PATCH /v2/admin/config/minter-epic with 'credential': null to clear.",
    deprecated = true
  )
  @APIResponse(responseCode = "410", description = "Endpoint removed. Use PATCH /v2/admin/config/minter-epic.")
  public Response clearCredential(@Context SecurityContext security) {
    return Response.status(Response.Status.GONE)
      .header("Location", "/v2/admin/config/minter-epic")
      .entity(GONE_MSG).build();
  }

  // ─── POST /test-connection ──────────────────────────────────────

  @POST
  @Path("/test-connection")
  @Operation(
    operationId = "testEpicMinterConnection",
    summary = "Diagnose connectivity to the configured ePIC API.",
    description = "Issues a GET against <apiBaseUrl> (or <apiBaseUrl>/handles if the root " +
    "redirects) and reports reachable/statusCode/latency. Useful for operators to verify " +
    "config before enabling minting."
  )
  @APIResponse(
    responseCode = "200",
    description = "Diagnostic result. reachable=true means the API responded 2xx-3xx; reachable=false " +
    "means network or non-2xx/3xx.",
    content = @Content(schema = @Schema(implementation = EpicTestConnectionIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response testConnection() {
    EpicMinterConfig cfg = service.current();
    String baseUrl = cfg.getApiBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      return Response.ok(
        new EpicTestConnectionIO(false, 0, 0L, null, "apiBaseUrl is not configured")
      ).build();
    }
    // Probe the API root — B2HANDLE-compatible servers respond to GET at
    // the API base URL or /handles with a JSON body.
    String probeUrl = stripTrailingSlash(baseUrl);
    long t0 = System.nanoTime();
    EpicHttpResponse response = http.getDiagnostic(probeUrl, null);
    long latencyMs = (System.nanoTime() - t0) / 1_000_000L;
    boolean reachable = response.statusCode() >= 200 && response.statusCode() < 400;
    String detail = reachable ? null : response.body();
    return Response.ok(
      new EpicTestConnectionIO(reachable, response.statusCode(), latencyMs, baseUrl, detail)
    ).build();
  }

  // ─── helpers ────────────────────────────────────────────────────

  private static String stripTrailingSlash(String s) {
    if (s == null) return "";
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }
}
