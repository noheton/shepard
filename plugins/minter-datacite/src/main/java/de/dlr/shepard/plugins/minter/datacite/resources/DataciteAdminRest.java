package de.dlr.shepard.plugins.minter.datacite.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient.DataciteHttpResponse;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteCredentialIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteCredentialSetIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteMinterConfigIO;
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
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * KIP1d — admin REST surface for the DataCite Fabrica minter plugin.
 *
 * <p>Lives under {@code /v2/admin/minters/datacite/...}. Class-level
 * {@code @RolesAllowed("instance-admin")} gate. Config fields are now
 * managed via {@code GET|PATCH /v2/admin/config/minter-datacite}
 * (V2CONV-A7); this resource retains the credential and test-connection
 * sister endpoints.
 *
 * <p>PROV1a captures every mutation via {@code ProvenanceCaptureFilter};
 * the filter records method + path + status only — the {@code POST
 * .../credential} plaintext never enters the {@code :Activity} audit trail.
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

  /** RFC 7807 type URIs for problem responses. */
  static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/minters.datacite.config.bad-request";

  @Inject
  DataciteMinterConfigService service;

  @Inject
  DataciteHttpClient http;

  // ─── POST /credential ───────────────────────────────────────────

  @POST
  @Path("/credential")
  @Operation(
    operationId = "setDataciteMinterCredential",
    summary = "Set or rotate the DataCite Member password.",
    description = "Body: {\"password\": \"<plaintext>\"}. The plaintext is encrypted with " +
    "AES-GCM keyed off the shepard instance id and stored on :DataciteMinterConfig. The " +
    "response carries only the fingerprint (first 8 hex of the SHA-256) — the plaintext is " +
    "never echoed. ProvenanceCaptureFilter captures the request method + path + status only, " +
    "so the plaintext does not enter the audit trail."
  )
  @APIResponse(
    responseCode = "200",
    description = "Credential stored successfully.",
    content = @Content(schema = @Schema(implementation = DataciteCredentialSetIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Empty / missing password.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response setCredential(DataciteCredentialIO body, @Context SecurityContext security) {
    if (body == null || body.password() == null || body.password().isBlank()) {
      return problem(
        PROBLEM_TYPE_BAD_REQUEST,
        "Empty credential",
        Status.BAD_REQUEST,
        "Body must carry a non-empty 'password' field."
      );
    }
    DataciteMinterConfig saved = service.setCredential(body.password(), callerName(security));
    DataciteCredentialSetIO out = new DataciteCredentialSetIO(
      true,
      DataciteMinterConfigService.fingerprint(saved.getPasswordHash())
    );
    return Response.ok(out).build();
  }

  // ─── DELETE /credential ─────────────────────────────────────────

  @DELETE
  @Path("/credential")
  @Operation(
    operationId = "clearDataciteMinterCredential",
    summary = "Clear the stored DataCite credential.",
    description = "Wipes :DataciteMinterConfig.passwordCipher + .passwordHash. Subsequent " +
    "mint calls throw publish.minter.failed until a fresh credential is set. The action is " +
    "captured as an :Activity row via PROV1a."
  )
  @APIResponse(
    responseCode = "200",
    description = "Credential cleared.",
    content = @Content(schema = @Schema(implementation = DataciteMinterConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response clearCredential(@Context SecurityContext security) {
    DataciteMinterConfig saved = service.clearCredential(callerName(security));
    return Response.ok(DataciteMinterConfigIO.from(saved)).build();
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

  private static String callerName(SecurityContext security) {
    if (security == null) return "unknown";
    var p = security.getUserPrincipal();
    if (p == null) return "unknown";
    String name = p.getName();
    return (name == null || name.isBlank()) ? "unknown" : name;
  }

  private Response problem(String type, String title, Status status, String detail) {
    ProblemJson body = new ProblemJson(type, title, status.getStatusCode(), detail, null);
    return Response.status(status).type("application/problem+json").entity(body).build();
  }
}
