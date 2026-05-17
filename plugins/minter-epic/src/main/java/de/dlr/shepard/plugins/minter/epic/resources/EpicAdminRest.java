package de.dlr.shepard.plugins.minter.epic.resources;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.minter.epic.daos.EpicHttpClient;
import de.dlr.shepard.plugins.minter.epic.daos.EpicHttpClient.EpicHttpResponse;
import de.dlr.shepard.plugins.minter.epic.entities.EpicMinterConfig;
import de.dlr.shepard.plugins.minter.epic.io.EpicCredentialIO;
import de.dlr.shepard.plugins.minter.epic.io.EpicCredentialSetIO;
import de.dlr.shepard.plugins.minter.epic.io.EpicMinterConfigIO;
import de.dlr.shepard.plugins.minter.epic.io.EpicMinterConfigPatchIO;
import de.dlr.shepard.plugins.minter.epic.io.EpicTestConnectionIO;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService.EpicPatch;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService.ReadOnlyFieldException;
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
 * KIP1c — admin REST surface for the ePIC handle service minter plugin.
 *
 * <p>Lives under {@code /v2/admin/minters/epic/...}. Class-level
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
 * @see EpicMinterConfigService
 * @see EpicMinterConfig
 */
@Path("/v2/admin/minters/epic")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)
@Tag(name = "Admin")
public class EpicAdminRest {

  /** RFC 7807 type URIs for problem responses. */
  static final String PROBLEM_TYPE_READ_ONLY_FIELD = "/problems/minters.epic.config.read-only-field";
  static final String PROBLEM_TYPE_BAD_REQUEST = "/problems/minters.epic.config.bad-request";

  @Inject
  EpicMinterConfigService service;

  @Inject
  EpicHttpClient http;

  // ─── GET /config ────────────────────────────────────────────────

  @GET
  @Path("/config")
  @Operation(
    summary = "Read the current :EpicMinterConfig singleton.",
    description = "Returns the runtime-mutable ePIC minter config — enabled, apiBaseUrl, handlePrefix. " +
    "The credential material is masked: credentialSet + an 8-hex fingerprint of the SHA-256 hash are " +
    "surfaced in place of the credential itself."
  )
  @APIResponse(
    responseCode = "200",
    description = "Current ePIC minter config (singleton).",
    content = @Content(schema = @Schema(implementation = EpicMinterConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response getConfig() {
    EpicMinterConfig cfg = service.current();
    return Response.ok(EpicMinterConfigIO.from(cfg)).build();
  }

  // ─── PATCH /config ──────────────────────────────────────────────

  @PATCH
  @Path("/config")
  @Operation(
    summary = "RFC 7396 merge-patch the :EpicMinterConfig singleton.",
    description = "Patchable fields: enabled, apiBaseUrl, handlePrefix. The credential fields " +
    "(credentialHash, credentialKey, credential) are read-only via this path — use " +
    "POST .../credential to set them. PROV1a captures this PATCH as an :Activity row."
  )
  @APIResponse(
    responseCode = "200",
    description = "Updated config returned in the same shape as GET.",
    content = @Content(schema = @Schema(implementation = EpicMinterConfigIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Caller patched a read-only field.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response patchConfig(EpicMinterConfigPatchIO body, @Context SecurityContext security) {
    EpicMinterConfigPatchIO patch = body == null ? new EpicMinterConfigPatchIO() : body;
    EpicPatch svc = new EpicPatch();
    svc.enabled = patch.getEnabled();
    svc.apiBaseUrl = patch.getApiBaseUrl();
    svc.apiBaseUrlTouched = patch.isApiBaseUrlTouched();
    svc.handlePrefix = patch.getHandlePrefix();
    svc.handlePrefixTouched = patch.isHandlePrefixTouched();
    svc.credentialHashTouched = patch.isCredentialHashTouched();
    svc.credentialKeyTouched = patch.isCredentialKeyTouched();

    final EpicMinterConfig saved;
    try {
      saved = service.patch(svc, callerName(security));
    } catch (ReadOnlyFieldException denied) {
      Log.warnf(
        "KIP1c: rejected PATCH /v2/admin/minters/epic/config — read-only field '%s' touched",
        denied.field()
      );
      return problem(
        PROBLEM_TYPE_READ_ONLY_FIELD,
        "Field is read-only via PATCH",
        Status.BAD_REQUEST,
        "Field '" +
        denied.field() +
        "' cannot be set via PATCH. Use POST /v2/admin/minters/epic/credential to mutate it."
      );
    } catch (IllegalArgumentException bad) {
      return problem(
        PROBLEM_TYPE_BAD_REQUEST,
        "Invalid patch value",
        Status.BAD_REQUEST,
        bad.getMessage()
      );
    }
    return Response.ok(EpicMinterConfigIO.from(saved)).build();
  }

  // ─── POST /credential ───────────────────────────────────────────

  @POST
  @Path("/credential")
  @Operation(
    summary = "Set or rotate the ePIC credential.",
    description = "Body: {\"credential\": \"<plaintext>\"}. The plaintext is encrypted with " +
    "AES-GCM keyed off the shepard instance id and stored on :EpicMinterConfig. The " +
    "response carries only the fingerprint (first 8 hex of the SHA-256) — the plaintext is " +
    "never echoed. ProvenanceCaptureFilter captures the request method + path + status only."
  )
  @APIResponse(
    responseCode = "200",
    description = "Credential stored successfully.",
    content = @Content(schema = @Schema(implementation = EpicCredentialSetIO.class))
  )
  @APIResponse(
    responseCode = "400",
    description = "Empty / missing credential.",
    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemJson.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response setCredential(EpicCredentialIO body, @Context SecurityContext security) {
    if (body == null || body.credential() == null || body.credential().isBlank()) {
      return problem(
        PROBLEM_TYPE_BAD_REQUEST,
        "Empty credential",
        Status.BAD_REQUEST,
        "Body must carry a non-empty 'credential' field."
      );
    }
    EpicMinterConfig saved = service.setCredential(body.credential(), callerName(security));
    EpicCredentialSetIO out = new EpicCredentialSetIO(
      true,
      EpicMinterConfigService.fingerprint(saved.getCredentialHash())
    );
    return Response.ok(out).build();
  }

  // ─── DELETE /credential ─────────────────────────────────────────

  @DELETE
  @Path("/credential")
  @Operation(
    summary = "Clear the stored ePIC credential.",
    description = "Wipes :EpicMinterConfig.credentialKey + .credentialHash. Subsequent " +
    "mint calls throw publish.minter.failed until a fresh credential is set. The action is " +
    "captured as an :Activity row via PROV1a."
  )
  @APIResponse(
    responseCode = "200",
    description = "Credential cleared.",
    content = @Content(schema = @Schema(implementation = EpicMinterConfigIO.class))
  )
  @APIResponse(responseCode = "403", description = "Caller lacks the instance-admin role.")
  public Response clearCredential(@Context SecurityContext security) {
    EpicMinterConfig saved = service.clearCredential(callerName(security));
    return Response.ok(EpicMinterConfigIO.from(saved)).build();
  }

  // ─── POST /test-connection ──────────────────────────────────────

  @POST
  @Path("/test-connection")
  @Operation(
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
