package de.dlr.shepard.plugins.minter.datacite.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient.DataciteHttpResponse;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteCredentialIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteCredentialSetIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteMinterConfigIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteMinterConfigPatchIO;
import de.dlr.shepard.plugins.minter.datacite.io.DataciteTestConnectionIO;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService.DatacitePatch;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService.ReadOnlyFieldException;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
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
 * {@code @RolesAllowed("instance-admin")} gate (credential management
 * is one of the highest-trust admin paths). Responses are
 * {@code application/json}; errors use the RFC 7807
 * {@code application/problem+json} envelope via {@link ProblemJson}.
 *
 * <p>PROV1a captures every mutation through this resource via
 * {@code ProvenanceCaptureFilter}; the filter records request method
 * + path + status only, never response bodies — so the {@code POST
 * .../credential} plaintext never enters the {@code :Activity}
 * audit trail.
 *
 * @see DataciteMinterConfigService
 * @see DataciteMinterConfig
 */
@Path("/v2/admin/minters/datacite")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class DataciteAdminRest {

  /** RFC 7807 type URIs for problem responses. */
  static final String PROBLEM_TYPE_READ_ONLY_FIELD = "/problems/minters.datacite.config.read-only-field";
  static final String PROBLEM_TYPE_BAD_STATE = "/problems/minters.datacite.config.bad-state";
  static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/minters.datacite.config.bad-request";

  @Inject
  DataciteMinterConfigService service;

  @Inject
  DataciteHttpClient http;

  // ─── GET /config ────────────────────────────────────────────────

  @GET
  @Path("/config")
  @Operation(
    summary = "Read the current :DataciteMinterConfig singleton.",
    description = "Returns the runtime-mutable DataCite minter config — enabled, apiBaseUrl, " +
    "handlePrefix, repositoryId, publisher, landingPageBase, defaultState. The credential " +
    "material is masked: passwordSet + an 8-hex fingerprint of the SHA-256 hash are surfaced " +
    "in place of the password itself."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current DataCite minter config (singleton).",
    content = @Content(schema = @Schema(implementation = DataciteMinterConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    DataciteMinterConfig cfg = service.current();
    return Response.ok(DataciteMinterConfigIO.from(cfg)).build();
  }

  // ─── PATCH /config ──────────────────────────────────────────────

  @PATCH
  @Path("/config")
  @Operation(
    summary = "RFC 7396 merge-patch the :DataciteMinterConfig singleton.",
    description = "Patchable fields: enabled, apiBaseUrl, handlePrefix, repositoryId, " +
    "publisher, landingPageBase, defaultState. The credential fields (passwordHash, " +
    "passwordCipher, password) are read-only via this path — use POST .../credential to " +
    "set them. PROV1a captures this PATCH as an :Activity row."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = DataciteMinterConfigIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Caller patched a read-only field or supplied an invalid state.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(DataciteMinterConfigPatchIO body, @Context SecurityContext security) {
    DataciteMinterConfigPatchIO patch = body == null ? new DataciteMinterConfigPatchIO() : body;
    DatacitePatch svc = new DatacitePatch();
    svc.enabled = patch.getEnabled();
    svc.apiBaseUrl = patch.getApiBaseUrl();
    svc.apiBaseUrlTouched = patch.isApiBaseUrlTouched();
    svc.handlePrefix = patch.getHandlePrefix();
    svc.handlePrefixTouched = patch.isHandlePrefixTouched();
    svc.repositoryId = patch.getRepositoryId();
    svc.repositoryIdTouched = patch.isRepositoryIdTouched();
    svc.publisher = patch.getPublisher();
    svc.publisherTouched = patch.isPublisherTouched();
    svc.landingPageBase = patch.getLandingPageBase();
    svc.landingPageBaseTouched = patch.isLandingPageBaseTouched();
    svc.defaultState = patch.getDefaultState();
    svc.defaultStateTouched = patch.isDefaultStateTouched();
    svc.passwordHashTouched = patch.isPasswordHashTouched();
    svc.passwordCipherTouched = patch.isPasswordCipherTouched();

    final DataciteMinterConfig saved;
    try {
      saved = service.patch(svc, callerName(security));
    } catch (ReadOnlyFieldException denied) {
      Log.warnf(
        "KIP1d: rejected PATCH /v2/admin/minters/datacite/config — read-only field '%s' touched",
        denied.field()
      );
      return problem(
        PROBLEM_TYPE_READ_ONLY_FIELD,
        "Field is read-only via PATCH",
        Status.BAD_REQUEST,
        "Field '" +
        denied.field() +
        "' cannot be set via PATCH. Use POST /v2/admin/minters/datacite/credential to mutate it."
      );
    } catch (IllegalArgumentException bad) {
      return problem(
        PROBLEM_TYPE_BAD_STATE,
        "Invalid patch value",
        Status.BAD_REQUEST,
        bad.getMessage()
      );
    }
    return Response.ok(DataciteMinterConfigIO.from(saved)).build();
  }

  // ─── POST /credential ───────────────────────────────────────────

  @POST
  @Path("/credential")
  @Operation(
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
